package io.github.korenkrita.simulsub

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/** Values stored in Prefs.asrBackend. */
const val ASR_SENSEVOICE = "sensevoice"
const val ASR_QWEN3 = "qwen3"

/** SenseVoice 在中文/日文词之间会插空格（它按词切分），字幕里不该有：只删两侧都是 CJK 的那个空格。 */
private val CJK_GAP = Regex("(?<=[\\u3000-\\u30ff\\u3400-\\u9fff\\uff00-\\uffef])[ \\u3000](?=[\\u3000-\\u30ff\\u3400-\\u9fff\\uff00-\\uffef])")

private fun tidy(s: String): String = s.replace(CJK_GAP, "")

/**
 * The speech models under [modelsDir], same layout as in the phone's APK assets:
 *   asr_sv/model.int8.onnx, asr_sv/tokens.txt  (SenseVoice)
 *   vad/silero_vad.onnx
 *
 * @param language "auto" 让它自己判语种；"ja"/"zh"/"en"/"ko"/"yue" 锁死一种。
 */
class SenseVoiceAsr(root: File, threads: Int, language: String = "ja") : Asr {
    private val rec = OfflineRecognizer(OfflineRecognizerConfig.builder().setOfflineModelConfig(OfflineModelConfig.builder()
        .setSenseVoice(OfflineSenseVoiceModelConfig.builder()
            .setModel(File(root, "asr_sv/model.int8.onnx").path)
            .setLanguage(language)
            .setInverseTextNormalization(true).build())
        .setTokens(File(root, "asr_sv/tokens.txt").path).setNumThreads(threads).setModelType("sensevoice").build())
        .setDecodingMethod("greedy_search").build())

    override fun recognize(samples: FloatArray): String = recognizeTimed(samples).text

    override fun recognizeTimed(samples: FloatArray): Recognition {
        val s = rec.createStream()
        try {
            s.acceptWaveform(Recognition.padded(samples), 16_000)
            rec.decode(s)
            val r = rec.getResult(s)
            return Recognition.of(tidy(r.text), r.tokens, r.timestamps, Recognition.PAD_S)
        } finally { s.release() }
    }

    fun release() = rec.release()
}

/**
 * Qwen3-ASR 0.6B: 30 languages and 22 Chinese dialects, about 940 MiB, much slower than SenseVoice.
 * Desktop only: the Android APK keeps SenseVoice (the model alone would push it past 1.2 GB).
 * sherpa-onnx 1.13.8 has no language option for Qwen3, so it identifies the language itself; [language] is only
 * forwarded for a future version that supports it. Its BPE tokens have no SentencePiece timings, so the result has no
 * pieces and the pipeline's lost-word repair stays off for this backend.
 */
class Qwen3Asr(root: File, threads: Int, language: String = "auto") : Asr {
    private val dir = File(root, "asr_qwen3")
    private val languageName = when (language) {
        "ja" -> "Japanese"; "zh" -> "Chinese"; "en" -> "English"; "ko" -> "Korean"; "yue" -> "Cantonese"; else -> ""
    }
    private val rec = OfflineRecognizer(OfflineRecognizerConfig.builder().setOfflineModelConfig(OfflineModelConfig.builder()
        .setQwen3Asr(OfflineQwen3AsrModelConfig.builder()
            .setConvFrontend(File(dir, "conv_frontend.onnx").path)
            .setEncoder(File(dir, "encoder.int8.onnx").path)
            .setDecoder(File(dir, "decoder.int8.onnx").path)
            .setTokenizer(File(dir, "tokenizer").path)
            .setMaxNewTokens(256)
            .setTemperature(1e-6f)
            .setTopP(0.8f)
            .setSeed(42)
            .build())
        .setNumThreads(threads).build())
        .setDecodingMethod("greedy_search").build())

    override fun recognize(samples: FloatArray): String = recognizeTimed(samples).text

    override fun recognizeTimed(samples: FloatArray): Recognition {
        val s = rec.createStream()
        try {
            if (languageName.isNotEmpty()) s.setOption("language", languageName)
            s.acceptWaveform(Recognition.padded(samples), 16_000)
            rec.decode(s)
            return Recognition(tidy(rec.getResult(s).text), null)
        } finally { s.release() }
    }

    fun release() = rec.release()

    companion object {
        /** What tools/fetch-qwen3.sh installs; the settings offer Qwen3 only when all of it is there. */
        fun available(root: File): Boolean {
            val dir = File(root, "asr_qwen3")
            return listOf("conv_frontend.onnx", "encoder.int8.onnx", "decoder.int8.onnx",
                "tokenizer/vocab.json", "tokenizer/merges.txt", "tokenizer/tokenizer_config.json")
                .all { File(dir, it).isFile }
        }
    }
}

/** Silero VAD for per-frame speech probability only; [Segmenter] cuts. */
class SileroProb(root: File) {
    private val vad = Vad(VadModelConfig.builder().setSileroVadModelConfig(SileroVadModelConfig.builder()
        .setModel(File(root, "vad/silero_vad.onnx").path).setWindowSize(512).build()).setSampleRate(16_000).setNumThreads(1).build())
    fun prob(frame: FloatArray): Float = vad.compute(frame)
    fun release() = vad.release()
}
