package com.bloodvitr.vitr.ytdlp

internal object YtDlpRuntimeBootstrap {
    fun initializeWithRecovery(
        initialize: () -> Unit,
        cleanup: () -> Unit
    ): Result<Unit> {
        val firstAttempt = runCatching(initialize)
        if (firstAttempt.isSuccess) {
            return firstAttempt
        }

        return runCatching {
            cleanup()
            initialize()
        }
    }
}
