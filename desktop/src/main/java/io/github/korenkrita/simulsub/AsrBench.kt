package io.github.korenkrita.simulsub

import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

private const val FRAME = 512
private const val RATE = 16_000

/**
 * Compares the desktop ASR backends on one WAV with the app's own VAD and [Segmenter]:
 *
 *   ./gradlew --configure-on-demand :desktop:asrBench -PbenchArgs="--wav live15.wav --ref live15.txt --backend both --lang ja"
 *
 * The reference is plain text (no timestamps). RTF counts every recognition the pipeline would run, drafts included;
 * --finals-only measures the "说完再出" mode instead. CER ignores punctuation and whitespace and compares the final
 * segments' text, in order, with the reference. --text-dir DIR writes each backend's text there as UTF-8 (the console
 * may not show Japanese punctuation correctly through Gradle).
 */
fun main(args: Array<String>) {
    preloadOnnxRuntime()
    val o = options(args)
    val wav = File(o["wav"] ?: error("--wav is required"))
    val ref = o["ref"]?.let { File(it).readText() }
    val lang = o["lang"] ?: "ja"
    val threads = o["threads"]?.toIntOrNull() ?: 4
    val finalsOnly = o.containsKey("finals-only")
    val textDir = o["text-dir"]?.let { File(it).apply { mkdirs() } }
    val wanted = when (o["backend"] ?: "both") {
        ASR_SENSEVOICE -> listOf(ASR_SENSEVOICE)
        ASR_QWEN3 -> listOf(ASR_QWEN3)
        "both" -> listOf(ASR_SENSEVOICE, ASR_QWEN3)
        else -> error("--backend must be sensevoice, qwen3 or both")
    }
    val audio = readWav(wav)
    val frames = frames(audio.samples)
    println("wav=${wav.path} audio=${fmt(audio.seconds)}s rate=${audio.sampleRate} channels=${audio.channels} frames=${frames.size}")
    val vad = SileroProb(modelsDir)
    try {
        val probs = FloatArray(frames.size) { vad.prob(frames[it]) }
        for (backend in wanted) {
            if (backend == ASR_QWEN3 && !Qwen3Asr.available(modelsDir)) {
                println("backend=qwen3 skipped: model missing at ${File(modelsDir, "asr_qwen3")}")
                continue
            }
            val asr: Asr
            val release: () -> Unit
            if (backend == ASR_QWEN3) { val a = Qwen3Asr(modelsDir, threads, lang); asr = a; release = a::release }
            else { val a = SenseVoiceAsr(modelsDir, threads, lang); asr = a; release = a::release }
            try {
                val r = run(asr, frames, probs, finalsOnly)
                val decode = r.decodeNs / 1e9
                val finalDecode = r.finalDecodeNs / 1e9
                val rtf = if (audio.seconds > 0) decode / audio.seconds else 0.0
                val finalRtf = if (audio.seconds > 0) finalDecode / audio.seconds else 0.0
                val cer = ref?.let { cer(it, r.text) }
                println("backend=$backend lang=$lang threads=$threads finals=${r.finals} drafts=${r.drafts} " +
                    "decode=${fmt(decode)}s rtf=${fmt(rtf)} finalDecode=${fmt(finalDecode)}s finalRtf=${fmt(finalRtf)}" +
                    (cer?.let { " cer=${fmt(it * 100)}%" } ?: ""))
                println("text<<<${r.text}>>>")
                textDir?.let { File(it, backend + if (finalsOnly) "-finals.txt" else ".txt").writeText(r.text) }
            } finally { release() }
        }
    } finally { vad.release() }
}

private class Run(val text: String, val finals: Int, val drafts: Int, val decodeNs: Long, val finalDecodeNs: Long)

private fun run(asr: Asr, frames: List<FloatArray>, probs: FloatArray, finalsOnly: Boolean): Run {
    val pieces = ArrayList<String>()
    var finals = 0
    var drafts = 0
    var decodeNs = 0L
    var finalDecodeNs = 0L
    val seg = Segmenter(
        emit = { job ->
            // the pipeline does not recognize drafts in "说完再出" mode
            if (job.isFinal || !finalsOnly) {
                val t0 = System.nanoTime()
                val text = asr.recognizeTimed(job.samples).text
                val dt = System.nanoTime() - t0
                decodeNs += dt
                if (job.isFinal) { finals++; finalDecodeNs += dt; pieces += text } else drafts++
            }
        },
        sampleRate = RATE,
        frameSize = FRAME,
        endSilenceMs = 350,
        draftEveryMs = 700,
        minDraftMs = 600,
        maxUtteranceMs = 6_000,
        preRollMs = 200,
    )
    val frameMs = FRAME * 1000 / RATE
    for (i in frames.indices) seg.accept(frames[i], probs[i], (i + 1L) * frameMs)
    seg.flush()
    return Run(pieces.joinToString("\n"), finals, drafts, decodeNs, finalDecodeNs)
}

private class Wav(val samples: FloatArray, val sampleRate: Int, val channels: Int, val seconds: Double)

/** 16 kHz mono float samples; other rates are linearly resampled and channels averaged. */
private fun readWav(file: File): Wav {
    val b = file.readBytes()
    require(b.size >= 12 && ascii(b, 0, 4) == "RIFF" && ascii(b, 8, 4) == "WAVE") { "not a WAV: $file" }
    var fmt = -1
    var channels = 0
    var rate = 0
    var bits = 0
    var data = -1
    var dataLen = 0
    var p = 12
    while (p + 8 <= b.size) {
        val id = ascii(b, p, 4)
        val len = le32(b, p + 4)
        p += 8
        require(len >= 0) { "bad chunk $id" }
        when (id) {
            "fmt " -> {
                require(len >= 16) { "short fmt chunk" }
                fmt = le16(b, p)
                channels = le16(b, p + 2)
                rate = le32(b, p + 4)
                bits = le16(b, p + 14)
                if (fmt == 0xFFFE && len >= 40) fmt = le16(b, p + 24)
            }
            "data" -> { data = p; dataLen = minOf(len, b.size - p) }
        }
        p += len + (len and 1)
    }
    require(channels > 0 && rate > 0 && bits % 8 == 0 && data >= 0) { "WAV has no usable fmt/data: $file" }
    val bytes = bits / 8
    val count = dataLen / (channels * bytes)
    val mono = FloatArray(count)
    for (i in 0 until count) {
        var sum = 0f
        for (c in 0 until channels) {
            val o = data + (i * channels + c) * bytes
            sum += when (fmt) {
                1 -> when (bits) {
                    8 -> ((b[o].toInt() and 0xFF) - 128) / 128f
                    16 -> le16(b, o).toShort() / 32768f
                    24 -> ((b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o + 2].toInt() shl 16)) / 8388608f
                    32 -> le32(b, o) / 2147483648f
                    else -> error("unsupported PCM $bits-bit")
                }
                3 -> {
                    require(bits == 32) { "unsupported float WAV" }
                    Float.fromBits(le32(b, o))
                }
                else -> error("unsupported WAV format $fmt")
            }
        }
        mono[i] = sum / channels
    }
    val seconds = count.toDouble() / rate
    if (rate == RATE) return Wav(mono, rate, channels, seconds)
    val n = (mono.size * RATE.toDouble() / rate).roundToInt().coerceAtLeast(1)
    val out = FloatArray(n) { i ->
        val pos = i * rate.toDouble() / RATE
        val j = pos.toInt().coerceIn(0, mono.size - 1)
        val k = (j + 1).coerceAtMost(mono.size - 1)
        val t = (pos - j).toFloat()
        mono[j] * (1 - t) + mono[k] * t
    }
    return Wav(out, RATE, channels, seconds)
}

private fun frames(samples: FloatArray): List<FloatArray> {
    val n = (samples.size + FRAME - 1) / FRAME
    return List(n) { i -> FloatArray(FRAME) { j -> samples.getOrElse(i * FRAME + j) { 0f } } }
}

private fun cer(ref: String, hyp: String): Double {
    val a = normalized(ref)
    val b = normalized(hyp)
    if (a.isEmpty()) return if (b.isEmpty()) 0.0 else 1.0
    var prev = IntArray(b.size + 1) { it }
    var cur = IntArray(b.size + 1)
    for (i in 1..a.size) {
        cur[0] = i
        for (j in 1..b.size) {
            cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        val t = prev; prev = cur; cur = t
    }
    return prev[b.size].toDouble() / a.size
}

private fun normalized(s: String): IntArray =
    Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase(Locale.ROOT).codePoints()
        .filter { Character.isLetterOrDigit(it) }.toArray()

private fun options(args: Array<String>): Map<String, String> {
    val m = LinkedHashMap<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        require(a.startsWith("--")) { "unexpected argument: $a" }
        val eq = a.indexOf('=')
        if (eq >= 0) {
            m[a.substring(2, eq)] = a.substring(eq + 1)
            i++
        } else {
            val key = a.substring(2)
            if (key == "finals-only") { m[key] = "true"; i++ }
            else {
                require(i + 1 < args.size) { "$a needs a value" }
                m[key] = args[i + 1]
                i += 2
            }
        }
    }
    return m
}

private fun ascii(b: ByteArray, off: Int, len: Int) = String(b, off, len, Charsets.US_ASCII)
private fun le16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
private fun le32(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or
    ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)
private fun fmt(x: Double) = String.format(Locale.ROOT, "%.2f", x)
