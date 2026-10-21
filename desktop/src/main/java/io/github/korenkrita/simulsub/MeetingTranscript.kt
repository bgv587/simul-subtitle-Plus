package io.github.korenkrita.simulsub

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val TITLE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * The finished sentences of each caption session on screen, kept after it stops so 记录 can export them.
 * One start→stop cycle is one segment; starting again keeps the previous segment instead of replacing it.
 * [add] runs on the pipeline's threads; the rest on the UI thread.
 */
object MeetingTranscript {
    class Snapshot internal constructor(
        val id: Long,
        val suggestedTitle: String,
        val startedAt: LocalDateTime,
        val sourceLang: String,
        val targetLang: String,
        val scene: String,
        val engine: String,
        val lines: List<TranscriptLine>,
        /** the session is still running (or paused): its lines can still grow */
        val active: Boolean,
    ) {
        /** the end of the last finished sentence: the meeting's length on the subtitle timeline */
        val durationMs: Long get() = lines.lastOrNull()?.endMs ?: 0
    }

    /** how many start/stop cycles this run keeps; old ones go first, they are only for export */
    private const val MAX_SESSIONS = 50

    private val lock = Any()
    private val sessions = ArrayList<Recorder>()
    private var current: Recorder? = null
    private var nextId = 1L

    /** A new session starts: its first line begins a new segment; the previous segment stays exportable. */
    fun begin(startedAt: LocalDateTime, sourceLang: String, targetLang: String, scene: String, engine: String) {
        synchronized(lock) {
            current?.active = false
            val r = Recorder(nextId++, startedAt, sourceLang, targetLang, scene, engine)
            sessions += r
            while (sessions.size > MAX_SESSIONS) sessions.removeAt(0)
            current = r
        }
    }

    fun add(line: TranscriptLine) { synchronized(lock) { current?.add(line) } }

    /** The session stopped: its segment is complete but stays on the list for export. */
    fun end() {
        synchronized(lock) {
            current?.active = false
            current = null
        }
    }

    /** The session on screen, or the last one (null before the first). */
    fun snapshot(): Snapshot? = synchronized(lock) { (current ?: sessions.lastOrNull())?.snapshot() }

    /** Every start/stop cycle this run, oldest first. */
    fun sessions(): List<Snapshot> = synchronized(lock) { sessions.map { it.snapshot() } }

    internal fun clear() {
        synchronized(lock) {
            sessions.clear()
            current = null
            nextId = 1L
        }
    }

    private class Recorder(
        private val id: Long,
        private val startedAt: LocalDateTime,
        private val sourceLang: String,
        private val targetLang: String,
        private val scene: String,
        private val engine: String,
    ) {
        var active = true

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
            id = id,
            suggestedTitle = "字幕记录 " + startedAt.format(TITLE_TIME),
            startedAt = startedAt,
            sourceLang = sourceLang,
            targetLang = targetLang,
            scene = scene,
            engine = engine,
            lines = lines.values.toList(),
            active = active,
        )
    }
}
