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

    private data class PlayerClientProfile(
        val clientName: String,
        val clientVersion: String,
        val extraFields: String = ""
    )

    private val playerProfiles = listOf(
        PlayerClientProfile(
            clientName = "IOS",
            clientVersion = "20.10.4",
            extraFields = """"deviceMake": "Apple", "deviceModel": "iPhone16,2","""
        ),
        PlayerClientProfile(
            clientName = "ANDROID_VR",
            clientVersion = "1.60.19",
            extraFields = """"deviceMake": "Oculus", "deviceModel": "Quest 3", "androidSdkVersion": 32,"""
        ),
        PlayerClientProfile(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            extraFields = ""
        )
    )

    suspend fun fetchHomeFeed(): List<VideoItem> = withContext(Dispatchers.IO) {
        val browseBody = """
            {
              "context": {
                "client": {
                  "clientName": "WEB",
                  "clientVersion": "2.20250312.01.00",
                  "hl": "en",
                  "gl": "US"
                }
              },
              "browseId": "FEwhat_to_watch"
            }
        """.trimIndent()
        val browseRoot = post("https://www.youtube.com/youtubei/v1/browse", browseBody)
        val browseItems = if (browseRoot != null) extractVideos(browseRoot).distinctBy { it.id } else emptyList()
        if (browseItems.isNotEmpty()) return@withContext browseItems

        // Fallback to curated car-friendly discovery query when anonymous browse is gated
        search("night drive music 4k")
    }

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val escaped = trimmed.replace("\\", "\\\\").replace("\"", "\\\"")
        val body = """
            {
              "context": {
                "client": {
                  "clientName": "WEB",
                  "clientVersion": "2.20250312.01.00",
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

    suspend fun resolveStream(videoId: String): StreamResult = withContext(Dispatchers.IO) {
        var lastError = "Failed to resolve playback stream."

        for (profile in playerProfiles) {
            val body = """
                {
                  "context": {
                    "client": {
                      "clientName": "${profile.clientName}",
                      "clientVersion": "${profile.clientVersion}",
                      ${profile.extraFields}
                      "hl": "en",
                      "gl": "US"
                    }
                  },
                  "videoId": "$videoId",
                  "contentCheckOk": true,
                  "racyCheckOk": true
                }
            """.trimIndent()
            val isIos = profile.clientName == "IOS"
            val root = post(
                url = "https://www.youtube.com/youtubei/v1/player",
                payload = body,
                userAgent = if (isIos) "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)" else null
            ) ?: continue
            when (val parsed = parsePlayerResponse(videoId, root)) {
                is StreamResult.Success -> {
                    val related = fetchRelated(videoId, parsed.stream.title, parsed.stream.channel)
                    return@withContext StreamResult.Success(parsed.stream.copy(relatedVideos = related))
                }
                is StreamResult.Error -> {
                    lastError = parsed.reason
                }
            }
        }
        StreamResult.Error(lastError)
    }

    internal fun parsePlayerResponse(videoId: String, root: JsonObject): StreamResult {
        val playability = root["playabilityStatus"]?.jsonObject
        val status = playability?.get("status")?.jsonPrimitive?.contentOrNull
        val reason = playability?.get("reason")?.jsonPrimitive?.contentOrNull

        val streamingData = root["streamingData"]?.jsonObject
        val hlsUrl = streamingData?.get("hlsManifestUrl")?.jsonPrimitive?.contentOrNull
        val progressiveUrl = streamingData?.get("formats")?.jsonArray
            ?.mapNotNull { it.jsonObject["url"]?.jsonPrimitive?.contentOrNull }
            ?.lastOrNull()
        val adaptive = streamingData?.get("adaptiveFormats")?.jsonArray
        val adaptiveVideoUrl = adaptive
            ?.firstOrNull {
                val obj = it.jsonObject
                val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                mime.startsWith("video/mp4") && obj["url"]?.jsonPrimitive?.contentOrNull != null
            }?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
        val adaptiveAudioUrl = adaptive
            ?.lastOrNull {
                val obj = it.jsonObject
                val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                mime.startsWith("audio/mp4") && obj["url"]?.jsonPrimitive?.contentOrNull != null
            }?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

        val finalUrl = hlsUrl ?: progressiveUrl ?: adaptiveVideoUrl

        if (finalUrl == null) {
            val message = reason
                ?: status?.let { "Playback unavailable ($it)." }
                ?: "No playable stream URL returned."
            return StreamResult.Error(message)
        }

        val details = root["videoDetails"]?.jsonObject
        val title = details?.get("title")?.jsonPrimitive?.contentOrNull ?: "Untitled"
        val channel = details?.get("author")?.jsonPrimitive?.contentOrNull ?: ""
        val thumb = details?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return StreamResult.Success(
            ResolvedStream(
                videoId = videoId,
                title = title,
                channel = channel,
                streamUrl = finalUrl,
                audioUrl = if (hlsUrl == null && progressiveUrl == null) adaptiveAudioUrl else null,
                isHls = hlsUrl != null,
                thumbnailUrl = thumb
            )
        )
    }

    private suspend fun fetchRelated(videoId: String, title: String, channel: String): List<VideoItem> {
        val nextBody = """
            {
              "context": {
                "client": {
                  "clientName": "WEB",
                  "clientVersion": "2.20250312.01.00",
                  "hl": "en",
                  "gl": "US"
                }
              },
              "videoId": "$videoId"
            }
        """.trimIndent()
        val nextRoot = post("https://www.youtube.com/youtubei/v1/next", nextBody)
        val fromNext = if (nextRoot != null) {
            extractVideos(nextRoot).filter { it.id != videoId }.distinctBy { it.id }.take(15)
        } else {
            emptyList()
        }
        if (fromNext.isNotEmpty()) return fromNext
        val query = channel.ifBlank { title.take(40) }
        return search(query).filter { it.id != videoId }.take(15)
    }

    private fun post(url: String, payload: String, userAgent: String? = null): JsonObject? {
        val builder = Request.Builder()
            .url(url)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (userAgent != null) {
            builder.header("User-Agent", userAgent)
        } else {
            builder.header("Origin", "https://www.youtube.com")
        }
        val request = builder.build()
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
        val rawThumb = obj["thumbnail"]?.jsonObject
            ?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull
        val thumb = when {
            rawThumb.isNullOrBlank() -> "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            rawThumb.startsWith("//") -> "https:$rawThumb"
            else -> rawThumb
        }
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
