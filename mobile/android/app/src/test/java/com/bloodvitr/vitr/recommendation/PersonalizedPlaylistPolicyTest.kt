package com.bloodvitr.vitr.recommendation

import com.bloodvitr.vitr.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizedPlaylistPolicyTest {
    private fun track(id: String, artist: String) = Track(
        id = id,
        title = "Song $id",
        artist = artist,
        album = "Catalog",
        streamUrl = "vitr-catalog://youtube/abcdefghijk",
        durationMs = 180_000L,
        artworkSeed = id.hashCode()
    )

    @Test
    fun buildsSpotifyInspiredVitrMixesWithoutDuplicateTracks() {
        val favorite = listOf(track("a", "Alpha"), track("b", "Alpha"))
        val discovery = listOf(track("c", "Beta"), track("d", "Gamma"))
        val trending = listOf(track("d", "Gamma"), track("e", "Delta"))

        val playlists = PersonalizedPlaylistPolicy.build(
            favoriteArtist = "Alpha",
            favoriteTracks = favorite,
            discoveryTracks = discovery,
            trendingTracks = trending
        )

        assertEquals(
            listOf("VITR Mix 1", "VITR Mix 2", "Discovery Mix", "Trending Mix"),
            playlists.map { it.title }
        )
        assertTrue(playlists.all { it.tracks.isNotEmpty() })
        assertTrue(playlists.all { section -> section.tracks.distinctBy(Track::id).size == section.tracks.size })
        assertTrue(playlists.first().subtitle.orEmpty().contains("Alpha"))
    }
}
