package io.roadie.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SponsorSegment(
    val startMs: Long,
    val endMs: Long,
    val category: String
)

/** Clean-room SponsorBlock API client for auto-skipping sponsored/intro/outro segments. */
class SponsorBlockClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId" +
            "&categories=%5B%22sponsor%22%2C%22selfpromo%22%2C%22interaction%22%2C%22intro%22%2C%22outro%22%5D"
        val request = Request.Builder().url(url).get().build()
        val raw = runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull() ?: return@withContext emptyList()

        parseSegments(raw)
    }

    internal fun parseSegments(rawJson: String): List<SponsorSegment> = runCatching {
        json.parseToJsonElement(rawJson).jsonArray.mapNotNull { item ->
            val obj = item.jsonObject
            val segment = obj["segment"]?.jsonArray ?: return@mapNotNull null
            if (segment.size < 2) return@mapNotNull null
            val startSec = segment[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val endSec = segment[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val category = obj["category"]?.jsonPrimitive?.content ?: "sponsor"
            if (endSec <= startSec) return@mapNotNull null
            SponsorSegment(
                startMs = (startSec * 1000.0).toLong(),
                endMs = (endSec * 1000.0).toLong(),
                category = category
            )
        }
    }.getOrDefault(emptyList())
}
