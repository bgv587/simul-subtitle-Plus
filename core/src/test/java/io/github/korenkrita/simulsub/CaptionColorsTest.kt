package io.github.korenkrita.simulsub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionColorsTest {
    @Test
    fun theThreeHexSpellingsAllReadAndOneOfThemWritesBack() {
        assertEquals(0xFFFFFFFF.toInt(), CaptionColors.parse("#FFF"))
        assertEquals(0xFFFF0000.toInt(), CaptionColors.parse("ff0000"))
        assertEquals(0x80FF8040.toInt(), CaptionColors.parse("  #80FF8040 "))
        assertNull(CaptionColors.parse(""))
        assertNull(CaptionColors.parse("#12345"))
        assertNull(CaptionColors.parse("zzzzzz"))
    }

    @Test
    fun aColourSurvivesARoundTripThroughTheField() {
        for (c in listOf(CaptionColors.DEFAULT_ZH, CaptionColors.DEFAULT_JA, 0xFF000000.toInt(), 0x80FF8040.toInt())) {
            assertEquals(c, CaptionColors.parse(CaptionColors.hex(c)))
        }
        assertEquals("FFFFFF", CaptionColors.hex(CaptionColors.DEFAULT_ZH))
        assertEquals("CCDCDCE4", CaptionColors.hex(CaptionColors.DEFAULT_JA))
    }

    @Test
    fun theDerivedShadesSitBetweenTheCaptionAndItsCard() {
        val zh = 0xFFFFFFFF.toInt(); val bg = 0xFF000000.toInt()
        assertEquals(0xFF8C8C8C.toInt(), CaptionColors.draft(zh, bg))
        assertEquals(0xFFC7C7C7.toInt(), CaptionColors.stats(zh, bg))
        // a draft below the finished sentence in brightness, and the timing line above it
        assertTrue(CaptionColors.draft(zh, bg) < CaptionColors.stats(zh, bg))
        // a see-through caption colour stays see-through
        assertEquals(0x80, CaptionColors.draft(0x80FF8040.toInt(), bg) ushr 24)
    }

    @Test
    fun darkTextOnALightCardIsGivenNoShadowAndLightTextIs() {
        assertTrue(CaptionColors.shadow(0xFFFFFFFF.toInt()))                    // 墨色、经典白
        assertTrue(CaptionColors.shadow(0xFFFFE066.toInt()))                     // 影院黄
        assertTrue(CaptionColors.shadow(0xFFA7F3D0.toInt()))                     // 薄荷绿
        assertTrue(CaptionColors.shadow(0xFFBAE6FD.toInt()))                     // 天空蓝
        assertTrue(CaptionColors.shadow(0xFFFBCFE8.toInt()))                     // 樱花粉
        assertFalse(CaptionColors.shadow(0xFF16151C.toInt()))                    // 白底黑字
        assertFalse(CaptionColors.shadow(0xFF000000.toInt()))
    }
}
