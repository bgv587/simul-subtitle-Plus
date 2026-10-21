package io.github.korenkrita.simulsub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class MeetingTranscriptTest {
    @Before
    fun reset() = MeetingTranscript.clear()

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
        assertTrue(s.active)
    }

    @Test
    fun everyStartStopCycleIsItsOwnSegment() {
        val first = LocalDateTime.of(2026, 10, 21, 9, 0)
        MeetingTranscript.begin(first, "日语", "中文", "会议", "SenseVoice → m")
        MeetingTranscript.add(TranscriptLine(1, 0, 1_000, "a", "甲"))
        MeetingTranscript.end()

        val stopped = MeetingTranscript.snapshot()!!
        assertEquals(listOf(1L), stopped.lines.map { it.id })
        assertFalse(stopped.active)

        val second = first.plusMinutes(30)
        MeetingTranscript.begin(second, "日语", "中文", "会议", "SenseVoice → m")
        MeetingTranscript.add(TranscriptLine(1, 0, 2_000, "b", "乙"))

        val sessions = MeetingTranscript.sessions()
        assertEquals(2, sessions.size)
        assertEquals("甲", sessions[0].lines.single().target)
        assertFalse(sessions[0].active)
        assertEquals("乙", sessions[1].lines.single().target)
        assertTrue(sessions[1].active)
        assertEquals(2L, sessions[1].id)
        assertEquals(2_000, MeetingTranscript.snapshot()!!.durationMs)
    }

    @Test
    fun anEmptySegmentIsKeptAndNewLinesGoToTheNewOne() {
        MeetingTranscript.begin(LocalDateTime.of(2026, 10, 21, 9, 0), "日语", "中文", "会议", "m")
        MeetingTranscript.end()
        MeetingTranscript.begin(LocalDateTime.of(2026, 10, 21, 9, 5), "日语", "中文", "会议", "m")
        MeetingTranscript.add(TranscriptLine(7, 0, 500, "c", "丙"))

        val sessions = MeetingTranscript.sessions()
        assertEquals(2, sessions.size)
        assertTrue(sessions[0].lines.isEmpty())
        assertEquals(listOf(7L), sessions[1].lines.map { it.id })
    }
}
