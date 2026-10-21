package io.github.korenkrita.simulsub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class CaptionFontsTest {

    @Test fun systemIsFirstAndHasNoFile() {
        val choices = CaptionFonts.choices(CaptionFonts.SYSTEM)
        assertEquals(CaptionFonts.SYSTEM, choices.first().id)
        assertNull(choices.first().file)
        for (c in choices.drop(1)) assertTrue("${c.id} 应指向存在的文件", c.file!!.isFile)
        assertEquals("选项不能重复", choices.size, choices.map { it.id }.distinct().size)
        assertNull(CaptionFonts.family(CaptionFonts.SYSTEM))
    }

    @Test fun everyOfferedInstalledFontOpens() {
        val choices = CaptionFonts.choices(CaptionFonts.SYSTEM).drop(1)
        assumeTrue("这台机器没有列表中的字体，跳过", choices.isNotEmpty())
        for (c in choices) assertNotNull("${c.label}（${c.id}）应能加载", CaptionFonts.family(c.id))
    }

    @Test fun pickedFontIsListedAndOpens() {
        val src = File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts/msyh.ttc")
        assumeTrue("没有微软雅黑，跳过", src.isFile)
        val copy = File.createTempFile("caption-font", ".ttc")
        try {
            src.copyTo(copy, overwrite = true)
            val id = CaptionFonts.customId(copy)
            val listed = CaptionFonts.choices(CaptionFonts.SYSTEM).firstOrNull { it.id == id }
            assertNull("没选过就不该出现", listed)
            val picked = CaptionFonts.choices(id).last()
            assertEquals(id, picked.id)
            assertEquals("${copy.nameWithoutExtension}（自己选的）", picked.label)
            val family = CaptionFonts.family(id)
            assertNotNull("用户选中的字体应能加载", family)
            assertSame("同一个字体文件应复用同一个家族", family, CaptionFonts.family(id))
        } finally {
            copy.delete()
        }
    }

    @Test fun badOrMissingPickedFontFallsBack() {
        assertNull(CaptionFonts.family("no-such-font"))

        val bad = File.createTempFile("caption-bad", ".ttf")
        try {
            bad.writeText("this is not a font")
            val id = CaptionFonts.customId(bad)
            assertNull("坏文件不能当作字体", CaptionFonts.file(id))
            assertNull("坏文件应回退到系统字体", CaptionFonts.family(id))
            assertEquals("${bad.nameWithoutExtension}（自己选的）", CaptionFonts.choices(id).last().label)
        } finally {
            bad.delete()
        }

        val gone = File(System.getProperty("java.io.tmpdir"), "caption-gone-${System.nanoTime()}.ttf")
        val goneId = CaptionFonts.customId(gone)
        assertNull(CaptionFonts.file(goneId))
        val listed = CaptionFonts.choices(goneId).last()
        assertEquals(goneId, listed.id)
        assertTrue("文件没了也要留在列表里提示：${listed.label}", listed.label.endsWith("（找不到文件）"))
    }

    @Test fun wrongExtensionIsRejected() {
        val src = File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts/msyh.ttc")
        assumeTrue("没有微软雅黑，跳过", src.isFile)
        val copy = File.createTempFile("caption-ext", ".txt")
        try {
            src.copyTo(copy, overwrite = true)
            assertNull("只接受 ttf/otf/ttc", CaptionFonts.file(CaptionFonts.customId(copy)))
        } finally {
            copy.delete()
        }
    }
}
