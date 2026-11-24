package io.roadie.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarCompanionServerTest {

    @Test
    fun `extractYouTubeVideoId handles watch, short, shorts, live, and raw IDs`() {
        assertEquals("dQw4w9WgXcQ", CarCompanionServer.extractYouTubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=12s"))
        assertEquals("dQw4w9WgXcQ", CarCompanionServer.extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("dQw4w9WgXcQ", CarCompanionServer.extractYouTubeVideoId("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", CarCompanionServer.extractYouTubeVideoId("dQw4w9WgXcQ"))
        assertNull(CarCompanionServer.extractYouTubeVideoId("https://example.com/not-youtube"))
    }
}
