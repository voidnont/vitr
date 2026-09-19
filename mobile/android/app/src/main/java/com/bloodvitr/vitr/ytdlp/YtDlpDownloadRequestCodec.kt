package com.bloodvitr.vitr.ytdlp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.bloodvitr.vitr.save.SaveFormat
import com.bloodvitr.vitr.save.SaveQuality

object YtDlpDownloadRequestCodec {
    private const val VERSION = 3
    private val mapper = ObjectMapper()

    fun encode(request: YtDlpDownloadRequest): String {
        val root = mapper.createObjectNode()
            .put("version", VERSION)
            .put("sourceUrl", request.sourceUrl)
            .put("title", request.title)
            .put("artist", request.artist)
            .put("mediaKind", request.mediaKind.name)
            .put("outputFormat", request.outputFormat.name)
            .put("quality", request.quality.name)
            .put("writeAutoSubtitles", request.writeAutoSubtitles)
            .put("embedSubtitles", request.embedSubtitles)
            .put("embedMetadata", request.embedMetadata)
            .put("embedThumbnail", request.embedThumbnail)
            .put("useAcceleratedDownloader", request.useAcceleratedDownloader)

        request.formatSelector?.let { root.put("formatSelector", it) }
        request.playlistEntryId?.let { root.put("playlistEntryId", it) }
        request.playlistIndex?.let { root.put("playlistIndex", it) }
        request.playlistTitle?.let { root.put("playlistTitle", it) }
        request.thumbnailUrl?.let { root.put("thumbnailUrl", it) }
        request.album?.let { root.put("album", it) }
        request.trackNumber?.takeIf { it > 0 }?.let { root.put("trackNumber", it) }
        request.discNumber?.takeIf { it > 0 }?.let { root.put("discNumber", it) }
        request.releaseYear?.takeIf { it in 1000..9999 }?.let { root.put("releaseYear", it) }
        request.templateId?.let { root.put("templateId", it) }

        root.putArray("subtitleLanguages").apply { request.subtitleLanguages.forEach(::add) }
        root.putArray("normalizedTemplateArgs").apply { request.normalizedTemplateArgs.forEach(::add) }

        return mapper.writeValueAsString(root)
    }

    fun decode(raw: String?): YtDlpDownloadRequest? = runCatching {
        val root = mapper.readTree(raw.orEmpty())
        val sourceUrl = root.text("sourceUrl")?.trim().orEmpty()
        if (sourceUrl.isBlank()) return@runCatching null

        YtDlpDownloadRequest(
            sourceUrl = sourceUrl,
            title = root.text("title").orEmpty(),
            artist = root.text("artist").orEmpty(),
            mediaKind = root.enumValue("mediaKind", YtDlpMediaKind.Audio),
            outputFormat = root.enumValue("outputFormat", SaveFormat.MP3),
            quality = root.enumValue("quality", SaveQuality.Mp3K320),
            formatSelector = root.text("formatSelector"),
            playlistEntryId = root.text("playlistEntryId"),
            playlistIndex = root["playlistIndex"]?.takeIf(JsonNode::isIntegralNumber)?.asInt(),
            playlistTitle = root.text("playlistTitle"),
            subtitleLanguages = root.stringList("subtitleLanguages"),
            writeAutoSubtitles = root.boolean("writeAutoSubtitles", false),
            embedSubtitles = root.boolean("embedSubtitles", false),
            embedMetadata = root.boolean("embedMetadata", true),
            embedThumbnail = root.boolean("embedThumbnail", true),
            thumbnailUrl = root.text("thumbnailUrl"),
            album = root.text("album"),
            trackNumber = root.positiveInt("trackNumber"),
            discNumber = root.positiveInt("discNumber"),
            releaseYear = root.intInRange("releaseYear", 1000..9999),
            templateId = root.text("templateId"),
            normalizedTemplateArgs = root.stringList("normalizedTemplateArgs"),
            useAcceleratedDownloader = root.boolean("useAcceleratedDownloader", true)
        )
    }.getOrNull()

    private fun JsonNode.text(name: String): String? =
        get(name)?.takeUnless { it.isNull || it.isMissingNode }?.asText()?.takeIf(String::isNotBlank)

    private fun JsonNode.boolean(name: String, default: Boolean): Boolean =
        get(name)?.takeIf(JsonNode::isBoolean)?.asBoolean() ?: default

    private fun JsonNode.positiveInt(name: String): Int? =
        get(name)
            ?.takeIf(JsonNode::isIntegralNumber)
            ?.asInt()
            ?.takeIf { it > 0 }

    private fun JsonNode.intInRange(name: String, range: IntRange): Int? =
        get(name)
            ?.takeIf(JsonNode::isIntegralNumber)
            ?.asInt()
            ?.takeIf(range::contains)

    private fun JsonNode.stringList(name: String): List<String> =
        get(name)?.takeIf(JsonNode::isArray)?.mapNotNull { node ->
            node.takeIf(JsonNode::isTextual)?.asText()?.takeIf(String::isNotBlank)
        }.orEmpty()

    private inline fun <reified T : Enum<T>> JsonNode.enumValue(name: String, default: T): T =
        text(name)?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } } ?: default
}
