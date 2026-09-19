package com.bloodvitr.vitr.playback

import com.bloodvitr.vitr.model.Track

object PreferredPlaybackSource {
    fun choose(
        requested: Track,
        candidates: List<Track>
    ): Track =
        candidates.firstOrNull { candidate ->
            candidate.id == requested.id &&
                isPersistentLocal(candidate.streamUrl)
        } ?: requested

    fun isPersistentLocal(
        raw: String?
    ): Boolean {
        val value = raw
            ?.trim()
            .orEmpty()

        return value.startsWith(
            "content://",
            ignoreCase = true
        ) || value.startsWith(
            "file://",
            ignoreCase = true
        )
    }
}
