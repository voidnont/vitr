package com.bloodvitr.vitr.source

internal object PlaybackRecoveryDecision {
    fun shouldRefreshAndRetry(
        result: PlaybackResolutionResult
    ): Boolean =
        result is PlaybackResolutionResult.Failed
}
