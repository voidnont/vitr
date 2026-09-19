package com.frxe.music.ytdlp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.math.roundToLong

object YtDlpInspectionParser {
    private val mapper = ObjectMapper()

    fun parse(json: String): YtDlpInspection {
        val root = mapper.readTree(json)
        val entries = root["entries"]
            ?.takeIf(JsonNode::isArray)
            ?.map(::parseEntry)
            .orEmpty()

        return YtDlpInspection(
            id = root.textOrNull("id"),
            title = root.textOrNull("title").orEmpty(),
            creator = root.firstText("uploader", "artist", "channel"),
            canonicalUrl = root.firstText("webpage_url", "original_url", "url"),
            thumbnailUrl = root.textOrNull("thumbnail"),
            durationMs = root.durationMs(),
            siteCategory = root.firstText("extractor_key", "extractor"),
            isPlaylist = root.textOrNull("_type") == "playlist" || entries.isNotEmpty(),
            formats = root["formats"]
                ?.takeIf(JsonNode::isArray)
                ?.map(::parseFormat)
                .orEmpty(),
            subtitleLanguages = root.objectFieldNames("subtitles"),
            automaticCaptionLanguages = root.objectFieldNames("automatic_captions"),
            entries = entries,
            album = root.textOrNull("album"),
            playlistTitle = root.firstText("playlist_title", "playlist"),
            playlistIndex = root.positiveIntOrNull("playlist_index"),
            trackNumber = root.positiveIntOrNull("track_number"),
            discNumber = root.positiveIntOrNull("disc_number"),
            releaseYear = root.releaseYearOrNull()
        )
    }

    private fun parseFormat(node: JsonNode): YtDlpMediaFormat =
        YtDlpMediaFormat(
            id = node.textOrNull("format_id").orEmpty(),
            extension = node.textOrNull("ext"),
            audioCodec = node.textOrNull("acodec"),
            videoCodec = node.textOrNull("vcodec"),
            audioBitrateKbps = node.doubleOrNull("abr"),
            totalBitrateKbps = node.doubleOrNull("tbr"),
            width = node.intOrNull("width"),
            height = node.intOrNull("height"),
            fps = node.doubleOrNull("fps"),
            approximateSizeBytes =
                node.longOrNull("filesize")
                    ?: node.longOrNull("filesize_approx")
        )

    private fun parseEntry(node: JsonNode): YtDlpPlaylistEntry =
        YtDlpPlaylistEntry(
            id = node.textOrNull("id"),
            title = node.textOrNull("title").orEmpty(),
            url = node.firstText("webpage_url", "url"),
            thumbnailUrl = node.textOrNull("thumbnail"),
            durationMs = node.durationMs(),
            creator = node.firstText("uploader", "artist", "channel"),
            playlistIndex = node.positiveIntOrNull("playlist_index")
        )

    private fun JsonNode.durationMs(): Long? =
        doubleOrNull("duration")?.times(1_000.0)?.roundToLong()

    private fun JsonNode.firstText(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            textOrNull(name)
        }

    private fun JsonNode.textOrNull(name: String): String? =
        get(name)
            ?.takeUnless { it.isNull || it.isMissingNode }
            ?.asText()
            ?.takeIf(String::isNotBlank)

    private fun JsonNode.doubleOrNull(name: String): Double? =
        get(name)
            ?.takeIf(JsonNode::isNumber)
            ?.asDouble()

    private fun JsonNode.intOrNull(name: String): Int? =
        get(name)
            ?.takeIf(JsonNode::isNumber)
            ?.asInt()

    private fun JsonNode.positiveIntOrNull(name: String): Int? =
        intOrNull(name)
            ?.takeIf { value -> value > 0 }

    private fun JsonNode.releaseYearOrNull(): Int? {
        val releaseYear =
            intOrNull("release_year")
                ?.takeIf { year -> year in 1000..9999 }
        if (releaseYear != null) {
            return releaseYear
        }

        return firstText("release_date", "upload_date")
            ?.take(4)
            ?.toIntOrNull()
            ?.takeIf { year -> year in 1000..9999 }
    }

    private fun JsonNode.longOrNull(name: String): Long? =
        get(name)
            ?.takeIf(JsonNode::isNumber)
            ?.asLong()

    private fun JsonNode.objectFieldNames(name: String): List<String> {
        val value = get(name)
            ?.takeIf(JsonNode::isObject)
            ?: return emptyList()

        return value.fieldNames().asSequence().toList()
    }
}
