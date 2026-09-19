package com.bloodvitr.vitr.ytdlp

enum class YtDlpCoreCapability { YtDlp, FFmpeg, Aria2c, Mutagen }

data class YtDlpCoreCapabilities(
    val ytDlpReady: Boolean = false,
    val ffmpegReady: Boolean = false,
    val aria2cReady: Boolean = false,
    val mutagenReady: Boolean = false
) {
    fun withCapability(capability: YtDlpCoreCapability, ready: Boolean): YtDlpCoreCapabilities = when (capability) {
        YtDlpCoreCapability.YtDlp -> copy(ytDlpReady = ready)
        YtDlpCoreCapability.FFmpeg -> copy(ffmpegReady = ready)
        YtDlpCoreCapability.Aria2c -> copy(aria2cReady = ready)
        YtDlpCoreCapability.Mutagen -> copy(mutagenReady = ready)
    }
}
