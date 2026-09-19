package com.frxe.music.ytdlp

import java.io.File

data class YtDlpDownloadResult(
    val mediaFile: File,
    val sidecarFiles: List<File> = emptyList(),
    val usedAcceleratedDownloader: Boolean = false
)
