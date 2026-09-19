package com.bloodvitr.vitr.source

object YtDlpPlaybackRecoveryPolicy {
    const val COOLDOWN_MS: Long = 15L * 60L * 1_000L

    fun shouldAttempt(
        lastAttemptAtMs: Long?,
        nowMs: Long
    ): Boolean {
        val lastAttempt = lastAttemptAtMs
            ?: return true
        val elapsed = nowMs - lastAttempt

        return elapsed < 0L || elapsed >= COOLDOWN_MS
    }
}
