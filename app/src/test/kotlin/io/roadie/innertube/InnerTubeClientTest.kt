package io.roadie.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeClientTest {

    @Test
    fun `extractVideos parses videoRenderer fields`() {
        val sample = """
            {
              "contents": {
                "videoRenderer": {
                  "videoId": "dQw4w9WgXcQ",
                  "title": { "runs": [{ "text": "Car Test Video" }] },
                  "ownerText": { "runs": [{ "text": "Roadie Channel" }] },
                  "lengthText": { "simpleText": "4:20" },
                  "shortViewCountText": { "simpleText": "1.2M views" }
                }
              }
            }
        """.trimIndent()
        val client = InnerTubeClient()
        val videos = client.extractVideos(Json.parseToJsonElement(sample))
        assertEquals(1, videos.size)
        assertEquals("dQw4w9WgXcQ", videos[0].id)
        assertEquals("Car Test Video", videos[0].title)
        assertEquals("Roadie Channel", videos[0].channel)
        assertEquals("4:20", videos[0].durationText)
    }

    @Test
    fun `parsePlayerResponse surfaces playability error reason`() {
        val sample = """
            {
              "playabilityStatus": {
                "status": "LOGIN_REQUIRED",
                "reason": "Sign in to confirm your age"
              }
            }
        """.trimIndent()
        val client = InnerTubeClient()
        val result = client.parsePlayerResponse("abc123", Json.parseToJsonElement(sample).jsonObject)
        assertTrue(result is StreamResult.Error)
        assertEquals("Sign in to confirm your age", (result as StreamResult.Error).reason)
    }
}
