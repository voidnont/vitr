package com.bloodvitr.vitr.ytdlp

object YtDlpDownloadCommandPolicy {

    fun arguments(
        request: YtDlpDownloadRequest,
        outputTemplate: String,
        useAria2c: Boolean,
        capabilities: YtDlpCoreCapabilities = YtDlpCore.capabilities
    ): List<YtDlpOption> = buildList {
        add(YtDlpOption("--ignore-config"))
        add(YtDlpOption("--no-warnings"))
        add(YtDlpOption("--output", outputTemplate))
        add(YtDlpOption("--concurrent-fragments", "8"))
        add(YtDlpOption("--socket-timeout", "15"))
        add(YtDlpOption("--retries", "3"))
        add(YtDlpOption("--fragment-retries", "3"))
        add(YtDlpOption("--extractor-retries", "2"))

        val isSingleItem =
            request.playlistEntryId.isNullOrBlank() &&
                request.playlistIndex == null

        if (isSingleItem) {
            add(YtDlpOption("--no-playlist"))
        } else {
            request.playlistIndex
                ?.takeIf { it > 0 }
                ?.let { index ->
                    add(YtDlpOption("--playlist-items", index.toString()))
                }
        }

        val selector = request.formatSelector
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: when (request.mediaKind) {
                YtDlpMediaKind.Audio -> "bestaudio/best"
                YtDlpMediaKind.Video -> "bestvideo*+bestaudio/best"
            }

        add(YtDlpOption("--format", selector))

        if (useAria2c) {
            add(YtDlpOption("--downloader", "libaria2c.so"))
            add(
                YtDlpOption(
                    "--downloader-args",
                    "aria2c:-x8 -s8 -k1M --file-allocation=none " +
                        "--connect-timeout=10 --timeout=15 --max-tries=3 --retry-wait=1"
                )
            )
        }

        addAll(
            YtDlpPostProcessingPolicy.arguments(
                request = request,
                capabilities = capabilities
            )
        )
    }
}
