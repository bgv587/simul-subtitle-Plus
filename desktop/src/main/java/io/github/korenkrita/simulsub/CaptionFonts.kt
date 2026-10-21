package io.github.korenkrita.simulsub

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import java.io.File

/** A font the caption card can draw with; [id] is what settings store. */
class CaptionFontChoice(val id: String, val label: String, val file: File?)

/**
 * The installed Windows fonts offered for the captions, and a font the user picked from disk. A file that cannot be
 * read or is not a TrueType/OpenType font falls back to the system font instead of breaking the caption window.
 */
object CaptionFonts {
    const val SYSTEM = "system"
    private const val FILE = "file:"
    private val windowsFonts = File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
    private val installed = listOf(
        "msyh.ttc" to "微软雅黑",
        "simhei.ttf" to "黑体",
        "simsun.ttc" to "宋体",
        "Deng.ttf" to "等线",
        "simkai.ttf" to "楷体",
        "simfang.ttf" to "仿宋",
        "msjh.ttc" to "微軟正黑體",
        "YuGothM.ttc" to "Yu Gothic（日文）",
        "meiryo.ttc" to "Meiryo（日文）",
        "msgothic.ttc" to "MS Gothic（日文）",
        "segoeui.ttf" to "Segoe UI",
        "arial.ttf" to "Arial",
        "times.ttf" to "Times New Roman",
        "consola.ttf" to "Consolas",
    )
    private val cache = HashMap<String, Pair<Long, FontFamily>>()

    fun customId(f: File): String = FILE + f.absolutePath

    /** The choices to show: system, the installed ones that exist, and [current] when it is a picked file. */
    fun choices(current: String): List<CaptionFontChoice> {
        val list = mutableListOf(CaptionFontChoice(SYSTEM, "系统默认", null))
        for ((name, label) in installed) {
            val f = File(windowsFonts, name)
            if (f.isFile) list += CaptionFontChoice(name, label, f)
        }
        if (current.startsWith(FILE)) {
            val f = File(current.removePrefix(FILE))
            val label = f.nameWithoutExtension + if (f.isFile) "（自己选的）" else "（找不到文件）"
            list += CaptionFontChoice(current, label, f)
        }
        return list
    }

    /** The font file behind [id]; null for the system font, or when it is no longer there. */
    fun file(id: String): File? = when {
        id == SYSTEM -> null
        id.startsWith(FILE) -> File(id.removePrefix(FILE)).takeIf { readableFont(it) }
        else -> installed.firstOrNull { it.first == id }?.let { File(windowsFonts, it.first) }?.takeIf { readableFont(it) }
    }

    /** The family to draw with; null keeps Compose's default. Cached by file, so recompositions reload nothing. */
    @Synchronized
    fun family(id: String): FontFamily? {
        val f = file(id) ?: return null
        val stamp = f.length() * 31 + f.lastModified()
        cache[id]?.let { if (it.first == stamp) return it.second }
        // load it once here: a damaged file falls back to the system font instead of failing text layout later
        val typeface = runCatching { org.jetbrains.skia.FontMgr.default.makeFromFile(f.path) }.getOrNull() ?: return null
        typeface.close()
        val family = runCatching { FontFamily(Font(f)) }.getOrNull() ?: return null
        cache[id] = stamp to family
        return family
    }

    /** A font Skia can open: a readable .ttf/.otf/.ttc whose header is a real font. */
    private fun readableFont(f: File): Boolean {
        if (!f.isFile || f.extension.lowercase() !in setOf("ttf", "otf", "ttc")) return false
        return runCatching {
            f.inputStream().use { s ->
                val h = ByteArray(4).also { s.read(it) }
                val tag = String(h, Charsets.ISO_8859_1)
                tag == "OTTO" || tag == "true" || tag == "typ1" || tag == "ttcf" ||
                    (h[0].toInt() == 0 && h[1].toInt() == 1 && h[2].toInt() == 0 && h[3].toInt() == 0)
            }
        }.getOrDefault(false)
    }
}
