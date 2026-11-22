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

/** Resolved playback streams and metadata for a video ID. */
@Serializable
data class ResolvedStream(
    val videoId: String,
    val title: String,
    val channel: String,
    val streamUrl: String,
    val isHls: Boolean = false,
    val thumbnailUrl: String = "",
    val relatedVideos: List<VideoItem> = emptyList()
)
