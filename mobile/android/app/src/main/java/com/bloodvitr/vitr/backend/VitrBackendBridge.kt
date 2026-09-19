package com.bloodvitr.vitr.backend

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.frxe.music.data.FrxeDatabase
import com.frxe.music.data.TrackEntity
import com.frxe.music.model.Track
import com.frxe.music.playback.FrxePlaybackService
import com.frxe.music.playback.PlaybackQueueStore
import com.frxe.music.playback.QueueRepeatMode
import com.frxe.music.source.YouTubeCatalogSource
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class VitrBackendBridge(
    private val activity: android.app.Activity,
    private val webView: WebView
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val source = YouTubeCatalogSource(activity.application)
    private val dao = FrxeDatabase.get(activity.applicationContext).libraryDao()
    private var controller: MediaController? = null

    private val controllerFuture =
        MediaController.Builder(
            activity,
            SessionToken(
                activity,
                ComponentName(
                    activity,
                    FrxePlaybackService::class.java
                )
            )
        ).buildAsync()

    private val mainExecutor = Executor { command ->
        Handler(Looper.getMainLooper()).post(command)
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            emitPlayerState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            emitPlayerState()
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            emitPlayerState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            emitPlayerState()
        }
    }

    init {
        controllerFuture.addListener(
            {
                controller = runCatching { controllerFuture.get() }
                    .getOrNull()
                    ?.also { it.addListener(playerListener) }
                emitPlayerState()
            },
            mainExecutor
        )
    }

    @JavascriptInterface
    fun search(query: String, requestId: String) {
        scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) { source.search(query) }
                reply(requestId, true, tracksJson(tracks))
            } catch (error: Throwable) {
                replyError(requestId, error)
            }
        }
    }

    @JavascriptInterface
    fun discover(query: String, requestId: String) {
        scope.launch {
            try {
                val clean = query.trim().ifBlank { "music" }
                val (tracks, albumHits, playlistHits) = withContext(Dispatchers.IO) {
                    Triple(
                        source.search(clean),
                        source.search("$clean album"),
                        source.search("$clean playlist")
                    )
                }

                val artists = JSONArray()
                tracks
                    .filter { it.artist.isNotBlank() }
                    .distinctBy { it.artist.lowercase() }
                    .take(16)
                    .forEach { track ->
                        artists.put(
                            catalogItemJson(
                                id = "artist:${track.artist.lowercase().hashCode()}",
                                kind = "artist",
                                title = track.artist,
                                subtitle = "Artist",
                                cover = track.artworkUrl,
                                searchQuery = track.artist
                            )
                        )
                    }

                val albums = JSONArray()
                albumHits
                    .distinctBy { "${it.title.lowercase()}|${it.artist.lowercase()}" }
                    .take(16)
                    .forEach { track ->
                        albums.put(
                            catalogItemJson(
                                id = "album:${track.id}",
                                kind = "album",
                                title = track.title,
                                subtitle = listOf("Album", track.artist)
                                    .filter(String::isNotBlank)
                                    .joinToString(" · "),
                                cover = track.artworkUrl,
                                searchQuery = "${track.title} ${track.artist}".trim()
                            )
                        )
                    }

                val playlists = JSONArray()
                playlistHits
                    .distinctBy { "${it.title.lowercase()}|${it.artist.lowercase()}" }
                    .take(16)
                    .forEach { track ->
                        playlists.put(
                            catalogItemJson(
                                id = "playlist:${track.id}",
                                kind = "playlist",
                                title = track.title,
                                subtitle = listOf("Playlist", track.artist)
                                    .filter(String::isNotBlank)
                                    .joinToString(" · "),
                                cover = track.artworkUrl,
                                searchQuery = "${track.title} playlist".trim()
                            )
                        )
                    }

                val genres = JSONArray()
                genreSuggestions(clean)
                    .forEach { genre ->
                        genres.put(
                            catalogItemJson(
                                id = "genre:${genre.lowercase().replace(" ", "-")}",
                                kind = "genre",
                                title = genre,
                                subtitle = "Genre",
                                cover = null,
                                searchQuery = "$genre music"
                            )
                        )
                    }

                reply(
                    requestId,
                    true,
                    JSONObject()
                        .put("tracks", tracksJson(tracks))
                        .put("artists", artists)
                        .put("albums", albums)
                        .put("playlists", playlists)
                        .put("genres", genres)
                )
            } catch (error: Throwable) {
                replyError(requestId, error)
            }
        }
    }

    @JavascriptInterface
    fun play(trackJson: String, queueJson: String, requestId: String) {
        scope.launch {
            try {
                val selected = parseTrack(JSONObject(trackJson))
                val requestedQueue = parseTracks(queueJson)
                val queue = if (requestedQueue.any { it.id == selected.id }) {
                    requestedQueue
                } else {
                    listOf(selected) + requestedQueue
                }.ifEmpty { listOf(selected) }

                PlaybackQueueStore.replaceAndRequestPlay(
                    tracks = queue,
                    currentTrackId = selected.id
                )
                controller?.play()

                reply(requestId, true, trackJson(selected))
                emitPlayerState()
            } catch (error: Throwable) {
                replyError(requestId, error)
            }
        }
    }

    @JavascriptInterface
    fun togglePlay() {
        controller?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
        emitPlayerState()
    }

    @JavascriptInterface
    fun pause() {
        controller?.pause()
        emitPlayerState()
    }

    @JavascriptInterface
    fun next() {
        val next = PlaybackQueueStore.advance(
            repeatMode = QueueRepeatMode.Off,
            shuffle = controller?.shuffleModeEnabled == true
        )
        next?.current?.entryId?.let(PlaybackQueueStore::selectAndRequestPlay)
        controller?.play()
        emitPlayerState()
    }

    @JavascriptInterface
    fun previous() {
        val previous = PlaybackQueueStore.previous(QueueRepeatMode.Off)
        previous?.current?.entryId?.let(PlaybackQueueStore::selectAndRequestPlay)
        controller?.play()
        emitPlayerState()
    }

    @JavascriptInterface
    fun seek(positionMs: Double) {
        controller?.seekTo(positionMs.toLong().coerceAtLeast(0L))
        emitPlayerState()
    }

    @JavascriptInterface
    fun setVolume(volume: Double) {
        controller?.volume = volume.toFloat().coerceIn(0f, 1f)
        emitPlayerState()
    }

    @JavascriptInterface
    fun state(requestId: String) {
        reply(requestId, true, playerStateJson())
    }

    @JavascriptInterface
    fun library(requestId: String) {
        scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    dao.observeAll().first().map(TrackEntity::asTrack)
                }
                reply(requestId, true, tracksJson(tracks))
            } catch (error: Throwable) {
                replyError(requestId, error)
            }
        }
    }

    @JavascriptInterface
    fun history(requestId: String) {
        scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    dao.observeHistory()
                        .first()
                        .map { it.asTrack() }
                        .distinctBy(Track::id)
                }
                reply(requestId, true, tracksJson(tracks))
            } catch (error: Throwable) {
                replyError(requestId, error)
            }
        }
    }

    @JavascriptInterface
    fun toggleFavorite(trackJson: String, requestId: String) {
        scope.launch {
            try {
                val track = parseTrack(JSONObject(trackJson))
                val liked = withContext(Dispatchers.IO) {
                    if (dao.contains(track.id)) {
                        dao.remove(track.id)
                        false
                    } else {
                        dao.save(TrackEntity.from(track))
                        true
                    }
                }
                reply(requestId, true, JSONObject().put("liked", liked))
            } catch (error: Throwable) {
                replyError(requestId, error)
            }
        }
    }

    private fun parseTracks(raw: String): List<Track> =
        runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(parseTrack(item))
                }
            }
        }.getOrDefault(emptyList())

    private fun parseTrack(json: JSONObject): Track {
        val id = json.optString("id").ifBlank {
            throw IllegalArgumentException("Track ID is required.")
        }
        return Track(
            id = id,
            title = json.optString("title", "Unknown track"),
            artist = json.optString("artist", "Unknown artist"),
            album = json.optString("album", ""),
            streamUrl = json.optString("streamUrl"),
            durationMs = json.optLong("durationMs", 0L),
            artworkSeed = json.optInt("artworkSeed", id.hashCode()),
            artworkUrl = json.optString("artworkUrl").takeIf(String::isNotBlank)
                ?: json.optString("cover").takeIf(String::isNotBlank)
                ?: json.optString("thumbnail").takeIf(String::isNotBlank),
            downloadUrl = json.optString("downloadUrl").takeIf(String::isNotBlank),
            originalStreamUrl = json.optString("originalStreamUrl").takeIf(String::isNotBlank)
        )
    }

    private fun catalogItemJson(
        id: String,
        kind: String,
        title: String,
        subtitle: String,
        cover: String?,
        searchQuery: String
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("kind", kind)
        .put("title", title)
        .put("subtitle", subtitle)
        .put("cover", cover ?: JSONObject.NULL)
        .put("searchQuery", searchQuery)

    private fun genreSuggestions(query: String): List<String> {
        val genres = listOf(
            "Pop", "Hip-hop", "R&B", "Rock", "Electronic", "Indie",
            "Jazz", "Classical", "Metal", "Afrobeats", "Latin", "Country",
            "Reggae", "K-pop", "Ambient", "Lo-fi", "Chill", "Workout",
            "Focus", "Party", "Sleep", "Romance", "Energy"
        )
        val clean = query.trim().lowercase()
        val matching = genres.filter { genre ->
            genre.lowercase().contains(clean) ||
                clean.contains(genre.lowercase())
        }
        return (if (matching.isNotEmpty()) matching else genres).take(16)
    }

    private fun trackJson(track: Track): JSONObject = JSONObject()
        .put("id", track.id)
        .put("kind", "youtube")
        .put("title", track.title)
        .put("artist", track.artist)
        .put("album", track.album)
        .put("streamUrl", track.streamUrl)
        .put("durationMs", track.durationMs)
        .put("durationSeconds", track.durationMs / 1000.0)
        .put("artworkSeed", track.artworkSeed)
        .put("artworkUrl", track.artworkUrl ?: JSONObject.NULL)
        .put("cover", track.artworkUrl ?: JSONObject.NULL)
        .put("downloadUrl", track.downloadUrl ?: JSONObject.NULL)
        .put("originalStreamUrl", track.originalStreamUrl ?: JSONObject.NULL)
        .put("source", "Vitr Android")

    private fun tracksJson(tracks: List<Track>): JSONArray =
        JSONArray().also { array -> tracks.forEach { array.put(trackJson(it)) } }

    private fun playerStateJson(): JSONObject {
        val player = controller
        val track = PlaybackQueueStore.currentTrack()
        return JSONObject()
            .put("track", track?.let(::trackJson) ?: JSONObject.NULL)
            .put("playing", player?.isPlaying == true)
            .put("positionMs", player?.currentPosition?.coerceAtLeast(0L) ?: 0L)
            .put(
                "durationMs",
                player?.duration?.takeIf { it > 0L }
                    ?: track?.durationMs
                    ?: 0L
            )
            .put("bufferedPercent", player?.bufferedPercentage ?: 0)
            .put("volume", player?.volume ?: 1f)
    }

    private fun reply(requestId: String, ok: Boolean, payload: Any) {
        val request = JSONObject.quote(requestId)
        val data = when (payload) {
            is JSONObject -> payload.toString()
            is JSONArray -> payload.toString()
            else -> JSONObject.wrap(payload).toString()
        }
        val script = "window.__vitrNativeResult&&window.__vitrNativeResult($request,$ok,$data);"
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun replyError(requestId: String, error: Throwable) {
        reply(
            requestId,
            false,
            JSONObject().put(
                "message",
                error.message
                    ?.lineSequence()
                    ?.firstOrNull()
                    ?.take(240)
                    ?: "Android backend error"
            )
        )
    }

    private fun emitPlayerState() {
        val payload = playerStateJson().toString()
        webView.post {
            webView.evaluateJavascript(
                "window.__vitrNativePlayerState&&window.__vitrNativePlayerState($payload);",
                null
            )
        }
    }

    fun release() {
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
        scope.cancel()
    }
}
