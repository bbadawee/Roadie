package io.roadie.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionImporterTest {

    @Test
    fun `parseChannelNames parses Google Takeout CSV with quoted channel titles`() {
        val csv = """
            Channel Id,Channel Url,Channel Title
            UC123,http://www.youtube.com/channel/UC123,Marques Brownlee
            UC456,http://www.youtube.com/channel/UC456,"Carwow, Official"
            UC123,http://www.youtube.com/channel/UC123,Marques Brownlee
        """.trimIndent()

        val channels = SubscriptionImporter.parseChannelNames(csv)
        assertEquals(listOf("Marques Brownlee", "Carwow, Official"), channels)
    }

    @Test
    fun `parseChannelNames parses NewPipe JSON and roundtrips Roadie export`() {
        val newPipeJson = """
            {
              "app_version": "0.28.0",
              "subscriptions": [
                {"service_id": 0, "url": "https://youtube.com/channel/1", "name": "Doug DeMuro"},
                {"service_id": 0, "url": "https://youtube.com/channel/2", "name": "Linus Tech Tips"}
              ]
            }
        """.trimIndent()

        val imported = SubscriptionImporter.parseChannelNames(newPipeJson)
        assertEquals(listOf("Doug DeMuro", "Linus Tech Tips"), imported)

        val exported = SubscriptionImporter.exportToJson(imported)
        val reimported = SubscriptionImporter.parseChannelNames(exported)
        assertEquals(listOf("Doug DeMuro", "Linus Tech Tips"), reimported)
    }
}
