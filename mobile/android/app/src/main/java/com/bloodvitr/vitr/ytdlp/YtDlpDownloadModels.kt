package com.bloodvitr.vitr.ytdlp

import com.bloodvitr.vitr.save.SaveFormat
import com.bloodvitr.vitr.save.SaveQuality

enum class YtDlpMediaKind { Audio, Video }

data class YtDlpDownloadRequest(
    val sourceUrl: String,
    val title: String,
    val artist: String,
    val mediaKind: YtDlpMediaKind = YtDlpMediaKind.Audio,
    val outputFormat: SaveFormat = SaveFormat.MP3,
    val quality: SaveQuality = SaveQuality.Mp3K320,
    val formatSelector: String? = null,
    val playlistEntryId: String? = null,
    val playlistIndex: Int? = null,
    val playlistTitle: String? = null,
    val subtitleLanguages: List<String> = emptyList(),
    val writeAutoSubtitles: Boolean = false,
    val embedSubtitles: Boolean = false,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = true,
    val thumbnailUrl: String? = null,
    val album: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val releaseYear: Int? = null,
    val templateId: String? = null,
    val normalizedTemplateArgs: List<String> = emptyList(),
    val useAcceleratedDownloader: Boolean = true
)
