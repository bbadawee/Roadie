package io.roadie.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterParserTest {

    @Test
    fun `parse extracts mm_ss and hh_mm_ss chapters in chronological order`() {
        val desc = """
            Check out our site: https://example.com
            0:00 Intro
            [02:15] - Cabin Tour
            1:05:30 Highway Noise Test
        """.trimIndent()
        val chapters = ChapterParser.parse(desc)
        assertEquals(3, chapters.size)
        assertEquals("Intro", chapters[0].title)
        assertEquals(0L, chapters[0].startMs)
        assertEquals("0:00", chapters[0].timestampLabel)
        assertEquals("Cabin Tour", chapters[1].title)
        assertEquals(135_000L, chapters[1].startMs)
        assertEquals("Highway Noise Test", chapters[2].title)
        assertEquals(3_930_000L, chapters[2].startMs)
        assertEquals("1:05:30", chapters[2].timestampLabel)
    }

    @Test
    fun `parse returns empty when fewer than two valid chapters exist`() {
        val desc = "0:00 Only one timestamp"
        assertTrue(ChapterParser.parse(desc).isEmpty())
    }
}
