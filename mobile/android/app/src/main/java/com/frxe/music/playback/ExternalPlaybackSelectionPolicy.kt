package com.frxe.music.playback

object ExternalPlaybackSelectionPolicy {
    fun shouldMirror(
        currentQueueTrackId: String?,
        selectedMediaId: String?
    ): Boolean {
        val selected = selectedMediaId
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return false

        return selected != currentQueueTrackId
    }
}
