package com.frxe.music.ytdlp

import com.frxe.music.intake.UrlIntakeParser

object YtDlpPlaylistExpansionPolicy {
    fun expand(
        inspection: YtDlpInspection,
        baseRequest: YtDlpDownloadRequest,
        selectedPlaylistIndices: Set<Int>
    ): List<YtDlpDownloadRequest> {
        if (!inspection.isPlaylist) return listOf(baseRequest)

        return inspection.entries.mapIndexedNotNull { zeroIndex, entry ->
            val sourceIndex = zeroIndex + 1
            if (sourceIndex !in selectedPlaylistIndices) return@mapIndexedNotNull null

            val sourceUrl =
                UrlIntakeParser.extractFirstHttpUrl(entry.url)
                    ?: return@mapIndexedNotNull null

            val playlistIndex =
                entry.playlistIndex
                    ?.takeIf { it > 0 }
                    ?: sourceIndex

            val playlistTitle =
                inspection.playlistTitle
                    ?.takeIf(String::isNotBlank)
                    ?: inspection.title.takeIf(String::isNotBlank)
                    ?: baseRequest.playlistTitle

            baseRequest.copy(
                sourceUrl = sourceUrl,
                title = entry.title.ifBlank { baseRequest.title },
                artist =
                    entry.creator
                        ?.takeIf(String::isNotBlank)
                        ?: inspection.creator?.takeIf(String::isNotBlank)
                        ?: baseRequest.artist,
                playlistEntryId = entry.id,
                playlistIndex = playlistIndex,
                playlistTitle = playlistTitle,
                thumbnailUrl =
                    entry.thumbnailUrl
                        ?.takeIf(String::isNotBlank)
                        ?: baseRequest.thumbnailUrl
            )
        }
    }
}
