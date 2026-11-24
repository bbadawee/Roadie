package io.roadie.carui

import io.roadie.data.BookmarkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoQualityAndBookmarkTest {

    @Test
    fun `VideoQualityPreset maps expected resolution caps`() {
        assertNull(VideoQualityPreset.AUTO.maxHeight)
        assertEquals(1080, VideoQualityPreset.P1080.maxHeight)
        assertEquals(720, VideoQualityPreset.P720.maxHeight)
        assertEquals(480, VideoQualityPreset.P480.maxHeight)
        assertEquals(360, VideoQualityPreset.P360.maxHeight)
    }

    @Test
    fun `BookmarkEntity converts cleanly to VideoItem`() {
        val entity = BookmarkEntity(
            videoId = "abc12345678",
            title = "Car Audio Test",
            channel = "Roadie",
            durationText = "5:20",
            viewCountText = "10K views",
            thumbnailUrl = "https://i.ytimg.com/vi/abc12345678/hqdefault.jpg",
            savedAtMs = 12345L
        )
        val item = entity.toVideoItem()
        assertEquals("abc12345678", item.id)
        assertEquals("Car Audio Test", item.title)
        assertEquals("Roadie", item.channel)
    }
}
