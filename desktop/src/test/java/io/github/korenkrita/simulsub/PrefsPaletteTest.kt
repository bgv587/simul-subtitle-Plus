package io.github.korenkrita.simulsub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * The user's own caption colours as [Prefs] keeps them: one edit for the three of them, the custom card built from
 * what is stored, and a preset id still meaning the preset even while custom colours sit in the settings file.
 */
class PrefsPaletteTest {
    @Test
    fun theSavedColoursBuildTheCustomCardAndFollowTheCaptionColour() {
        val file = File.createTempFile("simulsub-palette", ".json").also { it.delete() }
        try {
            val prefs = Prefs(file)
            prefs.palette = CaptionColors.CUSTOM_ID
            // a red caption on a blue card: the draft is red pulled into blue, and a bright caption keeps its shadow
            prefs.setColors(0xFFFF0000.toInt(), 0xCC00FF00.toInt(), 0xFF0000FF.toInt())
            val p = prefs.paletteOf()
            assertEquals(CaptionColors.CUSTOM_ID, p.id)
            assertEquals(CaptionColors.CUSTOM_LABEL, p.label)
            assertEquals(0xFFFF0000L, p.zh)
            assertEquals(0xCC00FF00L, p.ja)
            assertEquals(0xFF0000FFL, p.bg)
            assertEquals(CaptionColors.draft(0xFFFF0000.toInt(), 0xFF0000FF.toInt()).toLong() and 0xFFFF_FFFFL, p.draft)
            assertEquals(CaptionColors.shadow(0xFFFF0000.toInt()), p.shadow)
            // re-read from the file: what one edit wrote is what the next start gets
            val again = Prefs(file)
            assertEquals(p.zh, again.paletteOf().zh)
            assertEquals(p.ja, again.paletteOf().ja)
            assertEquals(p.bg, again.paletteOf().bg)
        } finally {
            file.delete()
        }
    }

    @Test
    fun aPresetIdIsAPresetEvenWhenCustomColoursAreSaved() {
        val file = File.createTempFile("simulsub-palette", ".json").also { it.delete() }
        try {
            val prefs = Prefs(file)
            prefs.setColors(0xFF000000.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
            assertEquals(PALETTES[0].id, prefs.paletteOf().id)
            prefs.palette = "paper"
            assertEquals("paper", prefs.paletteOf().id)
            assertFalse(prefs.paletteOf().shadow)
            // the custom chip itself still shows what was saved for it
            assertEquals(0xFF000000L, prefs.customOf().zh)
        } finally {
            file.delete()
        }
    }
}
