package io.roadie.carui

import kotlin.math.min

/**
 * Responsive car display metrics modeled on YouDash's logical viewport rules.
 * Ensures small head units retain multi-column browse and split-screen watch ergonomics.
 */
data class CarViewportMetrics(
    val widthDp: Float,
    val heightDp: Float
) {
    val contentScale: Float
        get() = if (widthDp <= 0f || heightDp <= 0f) 1f else min(1f, min(widthDp / 1024f, heightDp / 600f))

    val logicalWidthDp: Float
        get() = if (contentScale > 0f) widthDp / contentScale else widthDp

    val logicalHeightDp: Float
        get() = if (contentScale > 0f) heightDp / contentScale else heightDp

    val browseColumns: Int
        get() = when {
            logicalWidthDp >= 780f -> 3
            logicalWidthDp >= 500f -> 2
            else -> 1
        }

    val useWideWatchLayout: Boolean
        get() = logicalWidthDp >= 820f && logicalHeightDp >= 300f && logicalWidthDp > logicalHeightDp
}
