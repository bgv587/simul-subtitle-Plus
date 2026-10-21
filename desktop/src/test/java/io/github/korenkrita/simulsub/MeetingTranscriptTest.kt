package io.github.korenkrita.simulsub

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class MeetingTranscriptTest {
    @Test
    fun keepsFinalsInOrderAndReplacesADuplicateSegment() {
        MeetingTranscript.begin(LocalDateTime.of(2026, 10, 21, 14, 30), "日语", "中文", "会议", "SenseVoice → m")
        MeetingTranscript.add(TranscriptLine(1, 0, 1_000, "a", "甲"))
        MeetingTranscript.add(TranscriptLine(2, 1_000, 2_000, "b", "乙"))
        MeetingTranscript.add(TranscriptLine(1, 0, 1_200, "a2", "甲2"))
        MeetingTranscript.add(TranscriptLine(3, 2_000, 3_000, " ", " ")) // nothing said: not a sentence
        val s = MeetingTranscript.snapshot()!!
        assertEquals("字幕记录 2026-10-21 14:30", s.suggestedTitle)
        assertEquals(listOf(1L, 2L), s.lines.map { it.id })
        assertEquals("甲2", s.lines[0].target)
        assertEquals(1_200, s.lines[0].endMs)
        assertEquals(2_000, s.durationMs)
    }
}
