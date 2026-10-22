package io.github.korenkrita.simulsub

import kotlin.math.roundToInt

// The rules behind a user's own caption colours, shared so the phone and the desktop show the same card.

/**
 * The custom colour scheme (the stored palette id [CUSTOM_ID]): the user sets the caption text, the source text and
 * the card. The draft line and the timing line are derived from the caption colour instead of being a fourth choice,
 * so a hand-picked scheme cannot make the draft as bright as a finished sentence.
 */
object CaptionColors {
    const val CUSTOM_ID = "custom"
    const val CUSTOM_LABEL = "自定义"

    /** the starting point: 墨色, so a custom scheme nobody edited looks like the default one */
    val DEFAULT_ZH = 0xFFFFFFFF.toInt()
    val DEFAULT_JA = 0xCCDCDCE4.toInt()
    val DEFAULT_BG = 0xFF1A1A1E.toInt()

    /** how far a derived shade sits on the way into the card */
    private const val DRAFT_INTO_BG = 0.45
    private const val STATS_INTO_BG = 0.22

    fun draft(zh: Int, bg: Int): Int = toward(zh, bg, DRAFT_INTO_BG)

    fun stats(zh: Int, bg: Int): Int = toward(zh, bg, STATS_INTO_BG)

    /** "#RGB", "#RRGGBB" or "#AARRGGBB" (the # optional); null when it is not a colour */
    fun parse(text: String): Int? {
        val s = text.trim().removePrefix("#")
        val hex = if (s.length == 3) s.map { "$it$it" }.joinToString("") else s
        if (hex.length != 6 && hex.length != 8) return null
        val v = hex.toLongOrNull(16) ?: return null
        return (if (hex.length == 6) 0xFF00_0000L or v else v).toInt()
    }

    /** what the field shows: six digits when the colour is opaque, eight when it carries its own alpha */
    fun hex(color: Int): String =
        if (color ushr 24 == 0xFF) "%06X".format(color and 0xFF_FFFF) else "%08X".format(color.toLong() and 0xFFFF_FFFFL)

    /** dark text on a light card has no shadow: a black shadow layer would only blur it */
    fun shadow(zh: Int): Boolean =
        (0.299 * channel(zh, 16) + 0.587 * channel(zh, 8) + 0.114 * channel(zh, 0)) / 255.0 > 0.45

    /** [color] pulled [part] of the way into [bg], channel by channel, keeping [color]'s alpha */
    private fun toward(color: Int, bg: Int, part: Double): Int {
        fun ch(shift: Int): Int {
            val from = color shr shift and 0xFF
            val to = bg shr shift and 0xFF
            return (from + (to - from) * part).roundToInt()
        }
        return color and 0xFF00_0000.toInt() or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun channel(color: Int, shift: Int): Double = (color shr shift and 0xFF).toDouble()
}
