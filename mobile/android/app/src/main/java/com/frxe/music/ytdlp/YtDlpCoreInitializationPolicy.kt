package com.frxe.music.ytdlp

internal object YtDlpCoreInitializationPolicy {
    fun initialize(
        initializeYtDlp: () -> Unit,
        initializeFFmpeg: () -> Unit,
        initializeAria2c: () -> Unit
    ): YtDlpCoreCapabilities {
        var capabilities = YtDlpCoreCapabilities()

        fun initializeCapability(
            capability: YtDlpCoreCapability,
            block: () -> Unit
        ) {
            val ready = try {
                block()
                true
            } catch (_: Throwable) {
                false
            }

            capabilities = capabilities.withCapability(
                capability = capability,
                ready = ready
            )
        }

        initializeCapability(
            YtDlpCoreCapability.YtDlp,
            initializeYtDlp
        )
        initializeCapability(
            YtDlpCoreCapability.FFmpeg,
            initializeFFmpeg
        )
        initializeCapability(
            YtDlpCoreCapability.Aria2c,
            initializeAria2c
        )

        return capabilities
    }
}
