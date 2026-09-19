package com.frxe.music.ytdlp

sealed interface YtDlpInspectionResult {
    data class Success(
        val inspection: YtDlpInspection
    ) : YtDlpInspectionResult

    data class Failure(
        val message: String
    ) : YtDlpInspectionResult
}

data class YtDlpInspection(
    val id: String?,
    val title: String,
    val creator: String?,
    val canonicalUrl: String?,
    val thumbnailUrl: String?,
    val durationMs: Long?,
    val siteCategory: String?,
    val isPlaylist: Boolean,
    val formats: List<YtDlpMediaFormat>,
    val subtitleLanguages: List<String>,
    val automaticCaptionLanguages: List<String>,
    val entries: List<YtDlpPlaylistEntry>,
    val album: String? = null,
    val playlistTitle: String? = null,
    val playlistIndex: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val releaseYear: Int? = null
)

data class YtDlpMediaFormat(
    val id: String,
    val extension: String?,
    val audioCodec: String?,
    val videoCodec: String?,
    val audioBitrateKbps: Double?,
    val totalBitrateKbps: Double?,
    val width: Int?,
    val height: Int?,
    val fps: Double?,
    val approximateSizeBytes: Long?
)

data class YtDlpPlaylistEntry(
    val id: String?,
    val title: String,
    val url: String?,
    val thumbnailUrl: String?,
    val durationMs: Long?,
    val creator: String? = null,
    val playlistIndex: Int? = null
)
