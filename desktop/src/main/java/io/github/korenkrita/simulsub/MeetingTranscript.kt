package io.github.korenkrita.simulsub

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val TITLE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * The finished sentences of the session on screen, kept after it stops so 记录 can export the last meeting too.
 * [add] runs on the pipeline's threads; the rest on the UI thread.
 */
object MeetingTranscript {
    class Snapshot(
        val suggestedTitle: String,
        val startedAt: LocalDateTime,
        val sourceLang: String,
        val targetLang: String,
        val scene: String,
        val engine: String,
        val lines: List<TranscriptLine>,
    ) {
        /** the end of the last finished sentence: the meeting's length on the subtitle timeline */
        val durationMs: Long get() = lines.lastOrNull()?.endMs ?: 0
    }

    private val lock = Any()
    private var current: Recorder? = null

    /** A new session starts: the previous transcript is replaced. */
    fun begin(startedAt: LocalDateTime, sourceLang: String, targetLang: String, scene: String, engine: String) {
        synchronized(lock) { current = Recorder(startedAt, sourceLang, targetLang, scene, engine) }
    }

    fun add(line: TranscriptLine) { synchronized(lock) { current?.add(line) } }

    /** The session on screen, or the last one (null before the first). */
    fun snapshot(): Snapshot? = synchronized(lock) { current?.snapshot() }

    private class Recorder(
        private val startedAt: LocalDateTime,
        private val sourceLang: String,
        private val targetLang: String,
        private val scene: String,
        private val engine: String,
    ) {
        /** segment id → sentence; a later final of the same segment replaces the earlier one */
        private val lines = LinkedHashMap<Long, TranscriptLine>()
        private var synthetic = Long.MIN_VALUE

        fun add(l: TranscriptLine) {
            if (l.source.isBlank() && l.target.isBlank()) return
            val line = l.copy(source = l.source.trim(), target = l.target.trim())
            if (line.id < 0) {
                lines[synthetic++] = line
                return
            }
            val old = lines[line.id]
            if (old == null || line.target.isNotBlank() || old.target.isBlank()) lines[line.id] = line
        }

        fun snapshot() = Snapshot(
            suggestedTitle = "字幕记录 " + startedAt.format(TITLE_TIME),
            startedAt = startedAt,
            sourceLang = sourceLang,
            targetLang = targetLang,
            scene = scene,
            engine = engine,
            lines = lines.values.toList(),
        )
    }
}
