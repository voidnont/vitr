package com.bloodvitr.vitr.ytdlp

data class YtDlpOption(val option: String, val argument: String? = null)

object YtDlpPostProcessingPolicy {
    fun arguments(
        request: YtDlpDownloadRequest,
        capabilities: YtDlpCoreCapabilities
    ): List<YtDlpOption> = buildList {
        val languages = request.subtitleLanguages.map(String::trim).filter(String::isNotEmpty).distinct()
        if (languages.isNotEmpty()) {
            add(YtDlpOption("--write-subs"))
            add(YtDlpOption("--sub-langs", languages.joinToString(",")))
        }
        if (request.writeAutoSubtitles) add(YtDlpOption("--write-auto-subs"))
        if (request.embedSubtitles && request.mediaKind == YtDlpMediaKind.Video) {
            add(YtDlpOption("--embed-subs"))
        }

        val canEmbedAudioMetadata = request.mediaKind == YtDlpMediaKind.Video || capabilities.mutagenReady
        if (request.embedMetadata && canEmbedAudioMetadata) add(YtDlpOption("--embed-metadata"))

        if (request.embedThumbnail) {
            // Always keep the artwork sidecar so finalization can attach/copy it even
            // when this runtime cannot embed artwork directly (for example without Mutagen).
            add(YtDlpOption("--write-thumbnail"))
            if (canEmbedAudioMetadata) add(YtDlpOption("--embed-thumbnail"))
        }
    }
}
