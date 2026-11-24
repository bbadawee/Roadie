package io.roadie.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Clean-room DeArrow API client (https://sponsor.ajay.app/api/branding)
 * Replaces clickbait video titles with community-vetted titles.
 */
class DeArrowClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchCleanTitle(videoId: String): String? = withContext(Dispatchers.IO) {
        val url = "https://sponsor.ajay.app/api/branding?videoID=$videoId"
        val request = Request.Builder().url(url).get().build()
        val raw = runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull() ?: return@withContext null

        parseCleanTitle(raw)
    }

    internal fun parseCleanTitle(rawJson: String): String? = runCatching {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val titles = root["titles"]?.jsonArray ?: return@runCatching null
        val best = titles
            .mapNotNull { el ->
                val obj = el.jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim()
                val original = obj["original"]?.jsonPrimitive?.booleanOrNull ?: false
                val votes = obj["votes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                if (title.isNullOrBlank() || original || votes < 0) null else title to votes
            }
            .maxByOrNull { it.second }
            ?.first
        best?.replace(">", "")?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
