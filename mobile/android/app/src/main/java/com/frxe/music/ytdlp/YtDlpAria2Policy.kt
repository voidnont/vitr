package com.frxe.music.ytdlp

object YtDlpAria2Policy {
    fun shouldUse(
        request: YtDlpDownloadRequest,
        capabilities: YtDlpCoreCapabilities
    ): Boolean =
        request.useAcceleratedDownloader &&
            capabilities.ytDlpReady &&
            capabilities.aria2cReady
}
