package io.roadie.auth

import android.content.Context
import io.roadie.innertube.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class GoogleAccountSession(
    val accessToken: String,
    val refreshToken: String = "",
    val expiresAtMs: Long = 0L,
    val email: String = "",
    val displayName: String = ""
)

@Serializable
data class UserPlaylist(
    val id: String,
    val title: String,
    val itemCount: Int = 0,
    val thumbnailUrl: String = ""
)

data class DeviceCodePrompt(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int
)

sealed interface DeviceCodeStartResult {
    data class Ready(val prompt: DeviceCodePrompt) : DeviceCodeStartResult
    data class Error(val message: String) : DeviceCodeStartResult
}

sealed interface DeviceTokenPollResult {
    data class SignedIn(val session: GoogleAccountSession) : DeviceTokenPollResult
    data object Pending : DeviceTokenPollResult
    data object SlowDown : DeviceTokenPollResult
    data class Error(val message: String) : DeviceTokenPollResult
}

/**
 * OAuth 2.0 TV & Limited-Input Device Flow + YouTube Data API v3 client.
 * Works on both GMS phones and non-GMS aftermarket Android head units without
 * requiring Google Play Services.
 */
class YouTubeOAuthClient(
    private val context: Context,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getClientId(): String = prefs.getString(KEY_CLIENT_ID, "")?.trim().orEmpty()
    fun getClientSecret(): String = prefs.getString(KEY_CLIENT_SECRET, "")?.trim().orEmpty()

    fun saveOAuthCredentials(clientId: String, clientSecret: String) {
        prefs.edit()
            .putString(KEY_CLIENT_ID, clientId.trim())
            .putString(KEY_CLIENT_SECRET, clientSecret.trim())
            .apply()
    }

    fun getSavedSession(): GoogleAccountSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { json.decodeFromString<GoogleAccountSession>(raw) }.getOrNull()
    }

    fun saveSession(session: GoogleAccountSession) {
        prefs.edit().putString(KEY_SESSION, json.encodeToString(session)).apply()
    }

    fun clearSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    suspend fun startDeviceCodeFlow(
        clientId: String = getClientId()
    ): DeviceCodeStartResult = withContext(Dispatchers.IO) {
        if (clientId.isBlank()) {
            return@withContext DeviceCodeStartResult.Error(
                "Enter a Google OAuth TV Client ID in Settings first."
            )
        }
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", YOUTUBE_READONLY_SCOPE)
            .build()
        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/device/code")
            .post(body)
            .build()

        runCatching {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val obj = json.parseToJsonElement(raw).jsonObject
                if (!response.isSuccessful) {
                    val err = obj["error_description"]?.jsonPrimitive?.contentOrNull
                        ?: obj["error"]?.jsonPrimitive?.contentOrNull
                        ?: "HTTP ${response.code}"
                    return@withContext DeviceCodeStartResult.Error(err)
                }
                val deviceCode = obj["device_code"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val userCode = obj["user_code"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val verificationUrl = obj["verification_url"]?.jsonPrimitive?.contentOrNull
                    ?: "https://www.google.com/device"
                val interval = obj["interval"]?.jsonPrimitive?.intOrNull ?: 5
                val expiresIn = obj["expires_in"]?.jsonPrimitive?.intOrNull ?: 1800
                if (deviceCode.isBlank() || userCode.isBlank()) {
                    DeviceCodeStartResult.Error("Invalid device code response from Google.")
                } else {
                    DeviceCodeStartResult.Ready(
                        DeviceCodePrompt(
                            deviceCode = deviceCode,
                            userCode = userCode,
                            verificationUrl = verificationUrl,
                            intervalSeconds = interval.coerceAtLeast(3),
                            expiresInSeconds = expiresIn
                        )
                    )
                }
            }
        }.getOrElse { e ->
            DeviceCodeStartResult.Error(e.message ?: "Network error starting Google sign-in.")
        }
    }

    suspend fun pollForToken(
        deviceCode: String,
        clientId: String = getClientId(),
        clientSecret: String = getClientSecret()
    ): DeviceTokenPollResult = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
        if (clientSecret.isNotBlank()) {
            form.add("client_secret", clientSecret)
        }
        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(form.build())
            .build()

        runCatching {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val obj = json.parseToJsonElement(raw).jsonObject
                if (!response.isSuccessful) {
                    return@withContext when (obj["error"]?.jsonPrimitive?.contentOrNull) {
                        "authorization_pending" -> DeviceTokenPollResult.Pending
                        "slow_down" -> DeviceTokenPollResult.SlowDown
                        "access_denied" -> DeviceTokenPollResult.Error("Sign-in was cancelled.")
                        "expired_token" -> DeviceTokenPollResult.Error("Code expired. Please try again.")
                        else -> DeviceTokenPollResult.Error(
                            obj["error_description"]?.jsonPrimitive?.contentOrNull
                                ?: obj["error"]?.jsonPrimitive?.contentOrNull
                                ?: "Sign-in failed (HTTP ${response.code})."
                        )
                    }
                }
                val accessToken = obj["access_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val refreshToken = obj["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val expiresIn = obj["expires_in"]?.jsonPrimitive?.intOrNull ?: 3600
                if (accessToken.isBlank()) {
                    return@withContext DeviceTokenPollResult.Error("Missing access token.")
                }
                val (email, name) = fetchUserInfo(accessToken)
                val session = GoogleAccountSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAtMs = System.currentTimeMillis() + (expiresIn * 1000L),
                    email = email,
                    displayName = name
                )
                saveSession(session)
                DeviceTokenPollResult.SignedIn(session)
            }
        }.getOrElse { e ->
            DeviceTokenPollResult.Error(e.message ?: "Network error polling sign-in.")
        }
    }

    suspend fun ensureFreshAccessToken(): String? = withContext(Dispatchers.IO) {
        val current = getSavedSession() ?: return@withContext null
        if (System.currentTimeMillis() < current.expiresAtMs - 60_000L) {
            return@withContext current.accessToken
        }
        if (current.refreshToken.isBlank()) return@withContext current.accessToken
        val clientId = getClientId()
        if (clientId.isBlank()) return@withContext current.accessToken

        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("refresh_token", current.refreshToken)
            .add("grant_type", "refresh_token")
        val secret = getClientSecret()
        if (secret.isNotBlank()) form.add("client_secret", secret)

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(form.build())
            .build()

        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext current.accessToken
                val obj = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                val newAccess = obj["access_token"]?.jsonPrimitive?.contentOrNull ?: return@withContext current.accessToken
                val expiresIn = obj["expires_in"]?.jsonPrimitive?.intOrNull ?: 3600
                val updated = current.copy(
                    accessToken = newAccess,
                    expiresAtMs = System.currentTimeMillis() + (expiresIn * 1000L)
                )
                saveSession(updated)
                newAccess
            }
        }.getOrDefault(current.accessToken)
    }

    suspend fun fetchUserPlaylists(): List<UserPlaylist> = withContext(Dispatchers.IO) {
        val token = ensureFreshAccessToken() ?: return@withContext emptyList()
        val url = "https://www.googleapis.com/youtube/v3/playlists?part=snippet,contentDetails&mine=true&maxResults=25"
        val root = getAuthorizedJson(url, token) ?: return@withContext emptyList()
        val items = root["items"]?.jsonArray ?: return@withContext emptyList()
        items.mapNotNull { el ->
            val obj = el.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val snippet = obj["snippet"]?.jsonObject ?: return@mapNotNull null
            val title = snippet["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled Playlist"
            val count = obj["contentDetails"]?.jsonObject?.get("itemCount")?.jsonPrimitive?.intOrNull ?: 0
            val thumb = extractBestThumb(snippet["thumbnails"]?.jsonObject)
            UserPlaylist(id = id, title = title, itemCount = count, thumbnailUrl = thumb)
        }
    }

    suspend fun fetchPlaylistVideos(playlistId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val token = ensureFreshAccessToken() ?: return@withContext emptyList()
        val url = "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet,contentDetails&playlistId=$playlistId&maxResults=30"
        val root = getAuthorizedJson(url, token) ?: return@withContext emptyList()
        val items = root["items"]?.jsonArray ?: return@withContext emptyList()
        items.mapNotNull { el ->
            val snippet = el.jsonObject["snippet"]?.jsonObject ?: return@mapNotNull null
            val videoId = snippet["resourceId"]?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
                ?: el.jsonObject["contentDetails"]?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val title = snippet["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (title == "Private video" || title == "Deleted video") return@mapNotNull null
            val channel = snippet["videoOwnerChannelTitle"]?.jsonPrimitive?.contentOrNull
                ?: snippet["channelTitle"]?.jsonPrimitive?.contentOrNull
                ?: "YouTube"
            val thumb = extractBestThumb(snippet["thumbnails"]?.jsonObject)
                .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }
            VideoItem(
                id = videoId,
                title = title,
                channel = channel,
                thumbnailUrl = thumb
            )
        }
    }

    suspend fun fetchUserSubscriptions(): List<String> = withContext(Dispatchers.IO) {
        val token = ensureFreshAccessToken() ?: return@withContext emptyList()
        val url = "https://www.googleapis.com/youtube/v3/subscriptions?part=snippet&mine=true&maxResults=50"
        val root = getAuthorizedJson(url, token) ?: return@withContext emptyList()
        val items = root["items"]?.jsonArray ?: return@withContext emptyList()
        items.mapNotNull { el ->
            el.jsonObject["snippet"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
        }.distinct()
    }

    private fun fetchUserInfo(accessToken: String): Pair<String, String> {
        val obj = getAuthorizedJson("https://www.googleapis.com/oauth2/v2/userinfo", accessToken)
            ?: return "" to ""
        val email = obj["email"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return email to name
    }

    private fun getAuthorizedJson(url: String, accessToken: String): JsonObject? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = response.body?.string() ?: return@use null
                json.parseToJsonElement(raw).jsonObject
            }
        }.getOrNull()
    }

    private fun extractBestThumb(thumbs: JsonObject?): String {
        if (thumbs == null) return ""
        return listOf("high", "medium", "default")
            .firstNotNullOfOrNull { key ->
                thumbs[key]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
            }
            .orEmpty()
    }

    companion object {
        private const val PREFS_NAME = "roadie_auth"
        private const val KEY_CLIENT_ID = "oauth_client_id"
        private const val KEY_CLIENT_SECRET = "oauth_client_secret"
        private const val KEY_SESSION = "google_session"
        private const val YOUTUBE_READONLY_SCOPE =
            "https://www.googleapis.com/auth/youtube.readonly https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email"
    }
}
