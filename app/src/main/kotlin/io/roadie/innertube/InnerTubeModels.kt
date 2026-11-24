package io.roadie.innertube

import kotlinx.serialization.Serializable

/** Lightweight video item rendered in Browse, Search, and Up-Next queues. */
@Serializable
data class VideoItem(
    val id: String,
    val title: String,
    val channel: String,
    val durationText: String = "",
    val viewCountText: String = "",
    val thumbnailUrl: String = "",
    val isLive: Boolean = false
)

/** Timestamp chapter parsed from video description. */
@Serializable
data class VideoChapter(
    val title: String,
    val startMs: Long,
    val timestampLabel: String
)

/** Resolved playback streams and metadata for a video ID. */
@Serializable
data class ResolvedStream(
    val videoId: String,
    val title: String,
    val channel: String,
    val streamUrl: String,
    val audioUrl: String? = null,
    val isHls: Boolean = false,
    val thumbnailUrl: String = "",
    val chapters: List<VideoChapter> = emptyList(),
    val relatedVideos: List<VideoItem> = emptyList()
)

sealed interface StreamResult {
    data class Success(val stream: ResolvedStream) : StreamResult
    data class Error(val reason: String) : StreamResult
}
