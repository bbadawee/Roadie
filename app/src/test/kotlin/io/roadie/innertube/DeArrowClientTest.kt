package io.roadie.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeArrowClientTest {

    @Test
    fun `parseCleanTitle selects highest voted non-original title and strips casual marker`() {
        val sample = """
            {
              "titles": [
                {"title": "YOU WON'T BELIEVE THIS CAR!!", "original": true, "votes": 10},
                {"title": ">2026 Electric Sedan Review", "original": false, "votes": 4},
                {"title": "Low vote title", "original": false, "votes": 1}
              ]
            }
        """.trimIndent()
        assertEquals("2026 Electric Sedan Review", DeArrowClient().parseCleanTitle(sample))
    }

    @Test
    fun `parseCleanTitle returns null when only negative or original titles exist`() {
        val sample = """
            {
              "titles": [
                {"title": "Original Clickbait", "original": true, "votes": 5},
                {"title": "Spam", "original": false, "votes": -2}
              ]
            }
        """.trimIndent()
        assertNull(DeArrowClient().parseCleanTitle(sample))
    }
}
