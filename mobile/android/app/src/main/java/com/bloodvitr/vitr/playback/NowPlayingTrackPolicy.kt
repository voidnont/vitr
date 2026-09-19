package com.bloodvitr.vitr.playback

import com.bloodvitr.vitr.model.Track

object NowPlayingTrackPolicy {
    fun select(
        sessionTrack: Track?,
        queuedTrack: Track?
    ): Track? = sessionTrack ?: queuedTrack
}
