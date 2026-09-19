package com.frxe.music.source

import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackResolutionPrivacyTest {
    @Test
    fun normalPlaybackMessagesDoNotExposeResolverNames() {
        PlaybackResolutionMonitor.resolving("track")
        assertResolverNamesHidden()

        PlaybackResolutionMonitor.resolved(
            trackId = "track",
            resolver = PlaybackResolverKind.YtDlp
        )
        assertResolverNamesHidden()

        PlaybackResolutionMonitor.resolvingFallback(
            trackId = "track",
            fallbackName = "ZEXL"
        )
        assertResolverNamesHidden()

        PlaybackResolutionMonitor.resolvedFallback(
            trackId = "track",
            fallbackName = "ZEXL"
        )
        assertResolverNamesHidden()
    }

    private fun assertResolverNamesHidden() {
        val message = PlaybackResolutionMonitor.state.value.message.orEmpty().lowercase()
        listOf(
            "yt-dlp",
            "newpipe",
            "innertube",
            "zexl",
            "cobalt",
            "aria2",
            "mutagen"
        ).forEach { name ->
            assertFalse(
                "Normal playback status exposed backend name: $name",
                message.contains(name)
            )
        }
    }
}
