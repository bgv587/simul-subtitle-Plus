package io.github.korenkrita.simulsub

/** One finished sentence of a session, timed from its first captured audio. */
data class TranscriptLine(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val source: String,
    val target: String,
)

/** The documents 设置 › 记录 exports; ids are stored in settings, keep them stable. */
enum class TranscriptFormat(val id: String, val label: String, val extension: String) {
    MARKDOWN("markdown", "Markdown（会议纪要）", "md"),
    TXT("text", "纯文本", "txt"),
    SRT("srt", "SRT 字幕", "srt"),
    VTT("vtt", "WebVTT 字幕", "vtt"),
    JSON("json", "JSON（分析用）", "json");

    companion object {
        fun of(id: String?): TranscriptFormat = entries.firstOrNull { it.id == id } ?: MARKDOWN
    }
}

/** What an export says about its session; [startedAt] is already formatted for the user. */
data class TranscriptMeta(
    val title: String,
    val startedAt: String,
    val durationMs: Long,
    val sourceLang: String,
    val targetLang: String,
    val scene: String,
    val engine: String,
)

data class TranscriptOptions(
    val includeSource: Boolean = true,
    val includeTimestamps: Boolean = true,
    val includeMeta: Boolean = true,
)

/**
 * The captions of one session as a file for meeting notes or analysis. SRT, VTT and JSON always carry times; the
 * other two leave them out when [TranscriptOptions.includeTimestamps] is false.
 */
object TranscriptExport {
    fun format(format: TranscriptFormat, meta: TranscriptMeta, lines: List<TranscriptLine>,
               options: TranscriptOptions = TranscriptOptions()): String = when (format) {
        TranscriptFormat.MARKDOWN -> markdown(meta, lines, options)
        TranscriptFormat.TXT -> text(meta, lines, options)
        TranscriptFormat.SRT -> srt(lines, options)
        TranscriptFormat.VTT -> vtt(meta, lines, options)
        TranscriptFormat.JSON -> json(meta, lines, options)
    }

    /** HH:MM:SS, from the first captured audio */
    fun clock(ms: Long): String {
        val v = ms.coerceAtLeast(0)
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", v / 3_600_000, v / 60_000 % 60, v / 1_000 % 60)
    }

    private fun srtTime(ms: Long): String {
        val v = ms.coerceAtLeast(0)
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d,%03d",
            v / 3_600_000, v / 60_000 % 60, v / 1_000 % 60, v % 1_000)
    }

    private fun vttTime(ms: Long): String {
        val v = ms.coerceAtLeast(0)
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d.%03d",
            v / 3_600_000, v / 60_000 % 60, v / 1_000 % 60, v % 1_000)
    }

    fun duration(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1_000
        val h = s / 3_600
        val m = s / 60 % 60
        val sec = s % 60
        return when {
            h > 0 -> "$h 小时 $m 分 $sec 秒"
            m > 0 -> "$m 分 $sec 秒"
            else -> "$sec 秒"
        }
    }

    private fun markdown(meta: TranscriptMeta, lines: List<TranscriptLine>, o: TranscriptOptions): String = buildString {
        append("# ").append(meta.title.trim().ifEmpty { "字幕记录" }).append("\n\n")
        if (o.includeMeta) {
            append("- 时间：").append(meta.startedAt).append('\n')
            append("- 时长：").append(duration(meta.durationMs)).append('\n')
            append("- 语言：").append(meta.sourceLang).append(" → ").append(meta.targetLang).append('\n')
            if (meta.scene.isNotBlank()) append("- 场景：").append(meta.scene).append('\n')
            if (meta.engine.isNotBlank()) append("- 引擎：").append(meta.engine).append('\n')
            append("- 句数：").append(lines.size).append('\n')
            append("\n---\n\n")
        }
        for (l in lines) {
            if (o.includeTimestamps) append("**[").append(clock(l.startMs)).append("]**  ")
            append(l.target.ifBlank { l.source }.trim()).append("\n\n")
            if (o.includeSource && l.target.isNotBlank() && l.source.isNotBlank())
                append("> ").append(l.source.trim().replace("\n", "\n> ")).append("\n\n")
        }
    }.trimEnd() + "\n"

    private fun text(meta: TranscriptMeta, lines: List<TranscriptLine>, o: TranscriptOptions): String = buildString {
        append(meta.title.trim().ifEmpty { "字幕记录" }).append('\n')
        if (o.includeMeta) {
            append("时间：").append(meta.startedAt).append('\n')
            append("时长：").append(duration(meta.durationMs)).append('\n')
            append("语言：").append(meta.sourceLang).append(" → ").append(meta.targetLang).append('\n')
            if (meta.scene.isNotBlank()) append("场景：").append(meta.scene).append('\n')
            if (meta.engine.isNotBlank()) append("引擎：").append(meta.engine).append('\n')
            append("句数：").append(lines.size).append('\n')
        }
        append('\n')
        for (l in lines) {
            if (o.includeTimestamps) append('[').append(clock(l.startMs)).append("] ")
            append(l.target.ifBlank { l.source }.trim()).append('\n')
            if (o.includeSource && l.target.isNotBlank() && l.source.isNotBlank())
                append("  原文：").append(l.source.trim().replace("\n", "\n        ")).append('\n')
            append('\n')
        }
    }.trimEnd() + "\n"

    private fun srt(lines: List<TranscriptLine>, o: TranscriptOptions): String = buildString {
        for ((i, l) in lines.withIndex()) {
            val start = l.startMs.coerceAtLeast(0)
            val end = l.endMs.coerceAtLeast(start + 1)
            append(i + 1).append('\n')
            append(srtTime(start)).append(" --> ").append(srtTime(end)).append('\n')
            append(l.target.ifBlank { l.source }.trim()).append('\n')
            if (o.includeSource && l.target.isNotBlank() && l.source.isNotBlank()) append(l.source.trim()).append('\n')
            append('\n')
        }
    }

    private fun vtt(meta: TranscriptMeta, lines: List<TranscriptLine>, o: TranscriptOptions): String = buildString {
        append("WEBVTT\n\n")
        if (o.includeMeta) {
            val title = meta.title.trim()
            if (title.isNotEmpty() || meta.startedAt.isNotEmpty()) {
                append("NOTE\n")
                if (title.isNotEmpty()) append(title).append('\n')
                append(meta.startedAt).append(" · ").append(duration(meta.durationMs)).append('\n')
                append(meta.sourceLang).append(" → ").append(meta.targetLang)
                if (meta.scene.isNotBlank()) append(" · ").append(meta.scene)
                append("\n\n")
            }
        }
        for ((i, l) in lines.withIndex()) {
            val start = l.startMs.coerceAtLeast(0)
            val end = l.endMs.coerceAtLeast(start + 1)
            append(i + 1).append('\n')
            append(vttTime(start)).append(" --> ").append(vttTime(end)).append('\n')
            append(l.target.ifBlank { l.source }.trim()).append('\n')
            if (o.includeSource && l.target.isNotBlank() && l.source.isNotBlank()) append(l.source.trim()).append('\n')
            append('\n')
        }
    }

    private fun json(meta: TranscriptMeta, lines: List<TranscriptLine>, o: TranscriptOptions): String = buildString {
        append("{\n")
        if (o.includeMeta) {
            append("  \"title\": ").append(jsonString(meta.title)).append(",\n")
            append("  \"startedAt\": ").append(jsonString(meta.startedAt)).append(",\n")
            append("  \"durationMs\": ").append(meta.durationMs.coerceAtLeast(0)).append(",\n")
            append("  \"sourceLang\": ").append(jsonString(meta.sourceLang)).append(",\n")
            append("  \"targetLang\": ").append(jsonString(meta.targetLang)).append(",\n")
            append("  \"scene\": ").append(jsonString(meta.scene)).append(",\n")
            append("  \"engine\": ").append(jsonString(meta.engine)).append(",\n")
            append("  \"sentenceCount\": ").append(lines.size).append(",\n")
        }
        append("  \"sentences\": [")
        if (lines.isNotEmpty()) append('\n')
        for ((i, l) in lines.withIndex()) {
            append("    {\n")
            append("      \"id\": ").append(l.id).append(",\n")
            append("      \"startMs\": ").append(l.startMs.coerceAtLeast(0)).append(",\n")
            append("      \"endMs\": ").append(l.endMs.coerceAtLeast(l.startMs.coerceAtLeast(0))).append(",\n")
            if (o.includeSource) append("      \"source\": ").append(jsonString(l.source)).append(",\n")
            append("      \"target\": ").append(jsonString(l.target)).append('\n')
            append("    }").append(if (i < lines.lastIndex) "," else "").append('\n')
        }
        append("  ]\n}\n")
    }

    private fun jsonString(s: String): String = buildString {
        append('"')
        for (ch in s) when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (ch < ' ') append(String.format(java.util.Locale.ROOT, "\\u%04x", ch.code)) else append(ch)
        }
        append('"')
    }
}
