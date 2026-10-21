package io.github.korenkrita.simulsub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptExportTest {
    private val meta = TranscriptMeta(
        title = "周会 Q4",
        startedAt = "2026-10-21 14:30",
        durationMs = 65_400,
        sourceLang = "日语",
        targetLang = "中文",
        scene = "会议",
        engine = "SenseVoice → qwen-mt-turbo",
    )
    private val lines = listOf(
        TranscriptLine(1, 3_000, 5_500, "こんにちは", "你好"),
        TranscriptLine(2, 65_000, 67_000, "ありがとう", "谢谢"),
    )

    @Test fun clockCountsFromTheFirstCapturedAudio() {
        assertEquals("00:00:00", TranscriptExport.clock(0))
        assertEquals("00:01:23", TranscriptExport.clock(83_900))
        assertEquals("01:02:03", TranscriptExport.clock(3_723_000))
        assertEquals("00:00:00", TranscriptExport.clock(-5))
        assertEquals("1 分 5 秒", TranscriptExport.duration(65_400))
        assertEquals("1 小时 2 分 3 秒", TranscriptExport.duration(3_723_000))
    }

    @Test fun markdownIsMeetingNotesWithSourceQuotes() {
        val md = TranscriptExport.format(TranscriptFormat.MARKDOWN, meta, lines)
        assertTrue(md.startsWith("# 周会 Q4\n"))
        assertTrue(md.contains("- 时间：2026-10-21 14:30"))
        assertTrue(md.contains("- 时长：1 分 5 秒"))
        assertTrue(md.contains("- 语言：日语 → 中文"))
        assertTrue(md.contains("- 场景：会议"))
        assertTrue(md.contains("- 引擎：SenseVoice → qwen-mt-turbo"))
        assertTrue(md.contains("- 句数：2"))
        assertTrue(md.contains("**[00:00:03]**  你好"))
        assertTrue(md.contains("> こんにちは"))
        assertTrue(md.contains("**[00:01:05]**  谢谢"))
    }

    @Test fun markdownOptionsHideWhatTheySay() {
        val md = TranscriptExport.format(TranscriptFormat.MARKDOWN, meta, lines,
            TranscriptOptions(includeSource = false, includeTimestamps = false, includeMeta = false))
        assertTrue(md.startsWith("# 周会 Q4\n"))
        assertFalse(md.contains("- 时间："))
        assertFalse(md.contains("**[00:"))
        assertFalse(md.contains("> こんにちは"))
        assertTrue(md.contains("你好"))
        assertTrue(md.contains("谢谢"))
    }

    @Test fun textIsPlainWithIndentedSource() {
        val txt = TranscriptExport.format(TranscriptFormat.TXT, meta, lines)
        assertTrue(txt.startsWith("周会 Q4\n"))
        assertTrue(txt.contains("[00:00:03] 你好"))
        assertTrue(txt.contains("  原文：こんにちは"))
        assertTrue(txt.contains("语言：日语 → 中文"))
    }

    @Test fun srtAndVttAlwaysCarryTimes() {
        val srt = TranscriptExport.format(TranscriptFormat.SRT, meta, lines,
            TranscriptOptions(includeTimestamps = false, includeMeta = false))
        assertTrue(srt.startsWith("1\n00:00:03,000 --> 00:00:05,500\n你好\nこんにちは\n"))
        assertTrue(srt.contains("\n2\n00:01:05,000 --> 00:01:07,000\n谢谢\n"))
        val noSource = TranscriptExport.format(TranscriptFormat.SRT, meta, lines, TranscriptOptions(includeSource = false))
        assertFalse(noSource.contains("こんにちは"))

        val vtt = TranscriptExport.format(TranscriptFormat.VTT, meta, lines, TranscriptOptions(includeTimestamps = false))
        assertTrue(vtt.startsWith("WEBVTT\n\nNOTE\n周会 Q4\n2026-10-21 14:30 · 1 分 5 秒\n日语 → 中文 · 会议\n\n"))
        assertTrue(vtt.contains("00:00:03.000 --> 00:00:05.500"))
    }

    @Test fun aBadEndNeverMakesAnEmptyCue() {
        val srt = TranscriptExport.format(TranscriptFormat.SRT, meta,
            listOf(TranscriptLine(1, 1_000, 0, "", "一句")))
        assertTrue(srt.contains("00:00:01,000 --> 00:00:01,001"))
        assertFalse(srt.contains("00:00:00,000 --> 00:00:00,000"))
    }

    @Test fun jsonEscapesTextAndKeepsNumbers() {
        val weird = "他说\"好\"\n下一行\\路径\t!"
        val json = TranscriptExport.format(TranscriptFormat.JSON, meta,
            listOf(TranscriptLine(7, 1_500, 2_500, "原文", weird)))
        assertTrue(json.contains("  \"title\": \"周会 Q4\","))
        assertTrue(json.contains("  \"durationMs\": 65400,"))
        assertTrue(json.contains("      \"id\": 7,"))
        assertTrue(json.contains("      \"startMs\": 1500,"))
        assertTrue(json.contains("      \"endMs\": 2500,"))
        assertTrue(json.contains("      \"target\": \"他说\\\"好\\\"\\n下一行\\\\路径\\t!\""))
        assertTrue(json.trimEnd().endsWith("]\n}"))
    }

    @Test fun jsonCanBeSentencesOnly() {
        val json = TranscriptExport.format(TranscriptFormat.JSON, meta, lines,
            TranscriptOptions(includeSource = false, includeMeta = false))
        assertFalse(json.contains("\"title\""))
        assertFalse(json.contains("\"source\""))
        assertTrue(json.contains("\"sentences\": ["))
        assertTrue(json.contains("\"target\": \"你好\""))
    }
}
