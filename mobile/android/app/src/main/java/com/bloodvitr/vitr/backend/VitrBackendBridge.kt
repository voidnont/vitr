package com.bloodvitr.vitr.backend

import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.frxe.music.downloads.DownloadSupport
import com.frxe.music.model.Track
import com.frxe.music.source.PlaybackStreamResolver
import com.frxe.music.source.YouTubeCatalogSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(UnstableApi::class)
class VitrBackendBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val source = YouTubeCatalogSource(activity.application)
    private val resolver = PlaybackStreamResolver()
    private var currentTrack: Track? = null

    private val player: ExoPlayer = ExoPlayer.Builder(activity)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(activity)
                .setDataSourceFactory(DownloadSupport.dataSourceFactory(activity))
        )
        .build()
        .also { instance ->
            instance.addListener(
                object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        emitPlayerState()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        emitPlayerState()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        emitPlayerState()
                    }
                }
            )
        }

    @JavascriptInterface
    fun search(query: String, requestId: String) {
        scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    source.search(query)
                }
                val array = JSONArray()
                tracks.forEach { array.put(trackJson(it)) }
                reply(requestId, true, array)
            } catch (error: Throwable) {
                replyError(requestId, error)
            }
        }
    }

    @JavascriptInterface
    fun play(trackJson: String, requestId: String) {
        scope.launch {
            try {
                val requested = parseTrack(JSONObject(trackJson))
                val resolved = resolver.resolve(requested)
                    ?: throw IllegalStateException("Vitr could not resolve this track.")

                currentTrack = resolved
                player.setMediaItem(resolved.toMediaItem())
                player.prepare()
                player.play()

                reply(requestId, true, trackJson(resolved))
                emitPlayerState()
            } catch (error: Throwable) {
                replyError(requestId, error)
            }
        }
    }

    @JavascriptInterface
    fun togglePlay() {
        scope.launch {
            if (player.isPlaying) {
                player.pause()
            } else if (player.mediaItemCount > 0) {
                player.play()
            }
            emitPlayerState()
        }
    }

    @JavascriptInterface
    fun pause() {
        scope.launch {
            player.pause()
            emitPlayerState()
        }
    }

    @JavascriptInterface
    fun seek(positionMs: Double) {
        scope.launch {
            player.seekTo(positionMs.toLong().coerceAtLeast(0L))
            emitPlayerState()
        }
    }

    @JavascriptInterface
    fun state(requestId: String) {
        scope.launch {
            reply(requestId, true, playerStateJson())
        }
    }

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

    private fun playerStateJson(): JSONObject = JSONObject()
        .put("track", currentTrack?.let(::trackJson) ?: JSONObject.NULL)
        .put("playing", player.isPlaying)
        .put("positionMs", player.currentPosition.coerceAtLeast(0L))
        .put(
            "durationMs",
            player.duration.takeIf { it > 0L }
                ?: currentTrack?.durationMs
                ?: 0L
        )
        .put("bufferedPercent", player.bufferedPercentage)

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
        val payload = JSONObject()
            .put(
                "message",
                error.message
                    ?.lineSequence()
                    ?.firstOrNull()
                    ?.take(240)
                    ?: "Android backend error"
            )
        reply(requestId, false, payload)
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
        scope.cancel()
        player.release()
    }
}
