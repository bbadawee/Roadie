package io.roadie.innertube

/** Parses YouTube timestamp chapters (e.g. `0:00 Intro`, `1:05:30 Outro`) from video descriptions. */
object ChapterParser {
    private val timestampLineRegex = Regex(
        """^\s*(?:\[|\()?(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?:\]|\))?\s*[-–—:|]?\s*(.+?)\s*$"""
    )

    fun parse(description: String): List<VideoChapter> {
        if (description.isBlank()) return emptyList()
        val chapters = mutableListOf<VideoChapter>()
        val seenTimes = mutableSetOf<Long>()

        for (line in description.lineSequence()) {
            val match = timestampLineRegex.matchEntire(line) ?: continue
            val hours = match.groupValues[1].toLongOrNull() ?: 0L
            val minutes = match.groupValues[2].toLongOrNull() ?: continue
            val seconds = match.groupValues[3].toLongOrNull() ?: continue
            if (minutes >= 60 || seconds >= 60) continue

            val title = match.groupValues[4].trim()
            if (title.isEmpty() || title.startsWith("http", ignoreCase = true)) continue

            val timeMs = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
            if (seenTimes.add(timeMs)) {
                chapters.add(
                    VideoChapter(
                        title = title,
                        startMs = timeMs,
                        timestampLabel = formatTimestamp(hours, minutes, seconds)
                    )
                )
            }
        }
        val sorted = chapters.sortedBy { it.startMs }
        return if (sorted.size >= 2) sorted else emptyList()
    }

    private fun formatTimestamp(hours: Long, minutes: Long, seconds: Long): String =
        if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
}
