package io.roadie.innertube

import org.junit.Assert.assertEquals
import org.junit.Test

class SponsorBlockClientTest {

    @Test
    fun `parseSegments converts seconds to milliseconds and skips invalid ranges`() {
        val sample = """
            [
              {"segment": [12.5, 45.0], "category": "sponsor"},
              {"segment": [90.0, 90.0], "category": "outro"},
              {"segment": [100.0, 120.25], "category": "selfpromo"}
            ]
        """.trimIndent()
        val segments = SponsorBlockClient().parseSegments(sample)
        assertEquals(2, segments.size)
        assertEquals(12500L, segments[0].startMs)
        assertEquals(45000L, segments[0].endMs)
        assertEquals("sponsor", segments[0].category)
        assertEquals(100000L, segments[1].startMs)
        assertEquals(120250L, segments[1].endMs)
        assertEquals("selfpromo", segments[1].category)
    }
}
