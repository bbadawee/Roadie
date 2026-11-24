package io.roadie.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class RoadieBackupSubscription(val channelName: String)

@Serializable
data class RoadieBackupPayload(
    val app: String = "Roadie",
    val version: Int = 1,
    val subscriptions: List<RoadieBackupSubscription> = emptyList()
)

/**
 * Parses subscription exports from:
 * - Google Takeout `subscriptions.csv` (Channel Id, Channel Url, Channel Title)
 * - NewPipe / Tubular JSON (`subscriptions[].name`)
 * - Roadie JSON (`subscriptions[].channelName`)
 */
object SubscriptionImporter {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun parseChannelNames(rawContent: String): List<String> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return emptyList()

        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            parseJson(trimmed)
        } else {
            parseCsv(trimmed)
        }.distinct()
    }

    fun exportToJson(channels: Collection<String>): String {
        val payload = RoadieBackupPayload(
            subscriptions = channels.filter { it.isNotBlank() }.sorted().map { RoadieBackupSubscription(it) }
        )
        return json.encodeToString(payload)
    }

    private fun parseJson(raw: String): List<String> = runCatching {
        val root = json.parseToJsonElement(raw)
        val array = when {
            root is kotlinx.serialization.json.JsonArray -> root
            root.jsonObject["subscriptions"] != null -> root.jsonObject["subscriptions"]!!.jsonArray
            else -> return@runCatching emptyList()
        }
        array.mapNotNull { item ->
            val obj = item.jsonObject
            (obj["name"] ?: obj["channelName"] ?: obj["title"])
                ?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        }
    }.getOrDefault(emptyList())

    private fun parseCsv(raw: String): List<String> {
        val lines = raw.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()

        val headerCols = splitCsvLine(lines.first()).map { it.trim().lowercase() }
        val titleIdx = headerCols.indexOfFirst {
            it == "channel title" || it == "channel_title" || it == "title" || it == "name"
        }
        val startRow = if (titleIdx >= 0 || headerCols.any { it.contains("channel") }) 1 else 0

        return lines.drop(startRow).mapNotNull { line ->
            val cols = splitCsvLine(line)
            val candidate = when {
                titleIdx in cols.indices -> cols[titleIdx]
                cols.size >= 3 -> cols[2]
                else -> cols.lastOrNull()
            }
            candidate?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    out.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        out.add(current.toString())
        return out
    }
}
