package io.roadie.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Clean-room InnerTube API client for Browse, Search, and Player resolution.
 * Avoids Google Play Services and HTML scraping; parses structured JSON responses.
 */
class InnerTubeClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchHomeFeed(): List<VideoItem> = withContext(Dispatchers.IO) {
        val body = """
            {
              "context": {
                "client": {
                  "clientName": "ANDROID",
                  "clientVersion": "19.45.36",
                  "androidSdkVersion": 34,
                  "hl": "en",
                  "gl": "US"
                }
              },
              "browseId": "FEwhat_to_watch"
            }
        """.trimIndent()
        val root = post("https://www.youtube.com/youtubei/v1/browse", body) ?: return@withContext emptyList()
        extractVideos(root).distinctBy { it.id }
    }

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val escaped = trimmed.replace("\\", "\\\\").replace("\"", "\\\"")
        val body = """
            {
              "context": {
                "client": {
                  "clientName": "ANDROID",
                  "clientVersion": "19.45.36",
                  "androidSdkVersion": 34,
                  "hl": "en",
                  "gl": "US"
                }
              },
              "query": "$escaped"
            }
        """.trimIndent()
        val root = post("https://www.youtube.com/youtubei/v1/search", body) ?: return@withContext emptyList()
        extractVideos(root).distinctBy { it.id }
    }

    suspend fun resolveStream(videoId: String): ResolvedStream? = withContext(Dispatchers.IO) {
        val body = """
            {
              "context": {
                "client": {
                  "clientName": "IOS",
                  "clientVersion": "19.45.4",
                  "deviceMake": "Apple",
                  "deviceModel": "iPhone16,2",
                  "hl": "en",
                  "gl": "US"
                }
              },
              "videoId": "$videoId",
              "contentCheckOk": true,
              "racyCheckOk": true
            }
        """.trimIndent()
        val root = post("https://www.youtube.com/youtubei/v1/player", body) ?: return@withContext null
        val streamingData = root["streamingData"]?.jsonObject ?: return@withContext null
        val hlsUrl = streamingData["hlsManifestUrl"]?.jsonPrimitive?.contentOrNull
        val progressiveUrl = streamingData["formats"]?.jsonArray
            ?.mapNotNull { it.jsonObject["url"]?.jsonPrimitive?.contentOrNull }
            ?.lastOrNull()
        val finalUrl = hlsUrl ?: progressiveUrl ?: return@withContext null

        val details = root["videoDetails"]?.jsonObject
        val title = details?.get("title")?.jsonPrimitive?.contentOrNull ?: "Untitled"
        val channel = details?.get("author")?.jsonPrimitive?.contentOrNull ?: ""
        val thumb = details?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

        val nextBody = """
            {
              "context": {
                "client": {
                  "clientName": "ANDROID",
                  "clientVersion": "19.45.36",
                  "androidSdkVersion": 34
                }
              },
              "videoId": "$videoId"
            }
        """.trimIndent()
        val nextRoot = post("https://www.youtube.com/youtubei/v1/next", nextBody)
        val related = if (nextRoot != null) {
            extractVideos(nextRoot).filter { it.id != videoId }.distinctBy { it.id }.take(15)
        } else {
            emptyList()
        }

        ResolvedStream(
            videoId = videoId,
            title = title,
            channel = channel,
            streamUrl = finalUrl,
            isHls = hlsUrl != null,
            thumbnailUrl = thumb,
            relatedVideos = related
        )
    }

    private fun post(url: String, payload: String): JsonObject? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "com.google.android.youtube/19.45.36 (Linux; U; Android 14) gzip")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val raw = response.body?.string() ?: return null
                json.parseToJsonElement(raw).jsonObject
            }
        }.getOrNull()
    }

    internal fun extractVideos(element: JsonElement): List<VideoItem> {
        val results = mutableListOf<VideoItem>()
        walk(element, results)
        return results
    }

    private fun walk(element: JsonElement, out: MutableList<VideoItem>) {
        when (element) {
            is JsonObject -> {
                val renderer = element["videoRenderer"]?.jsonObject
                    ?: element["compactVideoRenderer"]?.jsonObject
                    ?: element["gridVideoRenderer"]?.jsonObject
                if (renderer != null) {
                    parseRenderer(renderer)?.let(out::add)
                }
                for (value in element.values) {
                    walk(value, out)
                }
            }
            is JsonArray -> {
                for (item in element) {
                    walk(item, out)
                }
            }
            else -> Unit
        }
    }

    private fun parseRenderer(obj: JsonObject): VideoItem? {
        val id = obj["videoId"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = readText(obj["title"]) ?: return null
        val channel = readText(obj["ownerText"]) ?: readText(obj["shortBylineText"]) ?: ""
        val duration = readText(obj["lengthText"]) ?: ""
        val views = readText(obj["shortViewCountText"]) ?: readText(obj["viewCountText"]) ?: ""
        val thumb = obj["thumbnail"]?.jsonObject
            ?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull
            ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        return VideoItem(
            id = id,
            title = title,
            channel = channel,
            durationText = duration,
            viewCountText = views,
            thumbnailUrl = thumb,
            isLive = duration.isEmpty() && views.contains("watching", ignoreCase = true)
        )
    }

    private fun readText(element: JsonElement?): String? {
        val obj = element as? JsonObject ?: return null
        obj["simpleText"]?.jsonPrimitive?.contentOrNull?.let { return it }
        val runs = obj["runs"] as? JsonArray ?: return null
        return runs.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")
            .takeIf { it.isNotBlank() }
    }
}
