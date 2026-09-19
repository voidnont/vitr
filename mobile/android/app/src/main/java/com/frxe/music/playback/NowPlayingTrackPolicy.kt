package com.frxe.music.playback

import com.frxe.music.model.Track

object NowPlayingTrackPolicy {
    fun select(
        sessionTrack: Track?,
        queuedTrack: Track?
    ): Track? = sessionTrack ?: queuedTrack
}
