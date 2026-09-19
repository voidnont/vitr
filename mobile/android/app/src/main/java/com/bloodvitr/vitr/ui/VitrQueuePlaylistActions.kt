package com.bloodvitr.vitr.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.bloodvitr.vitr.data.PlaylistRepository
import com.bloodvitr.vitr.model.VitrRepeatMode
import com.bloodvitr.vitr.model.Track
import com.bloodvitr.vitr.playback.PlaybackQueueState
import com.bloodvitr.vitr.playback.PlaybackQueueStore
import com.bloodvitr.vitr.playback.QueueRepeatMode
import com.bloodvitr.vitr.save.VitrDownloadService
import com.bloodvitr.vitr.save.SaveFormat
import com.bloodvitr.vitr.save.SaveRequest
import com.bloodvitr.vitr.save.automaticDownloadSource
import com.bloodvitr.vitr.save.defaultQualityFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val VitrViewModel.playbackQueueState:
    StateFlow<PlaybackQueueState>
    get() = PlaybackQueueStore.state

fun VitrViewModel.playQueued(
    track: Track,
    queue: List<Track> = emptyList()
) {
    play(track, queue)
}

fun VitrViewModel.playNext(
    track: Track
) {
    PlaybackQueueStore.playNext(track)
}

fun VitrViewModel.addToQueue(
    track: Track
) {
    PlaybackQueueStore.append(track)
}

fun VitrViewModel.selectQueueEntry(
    entryId: String
) {
    PlaybackQueueStore.selectAndRequestPlay(entryId)
}

fun VitrViewModel.removeQueueEntry(
    entryId: String
) {
    PlaybackQueueStore.remove(entryId)
}

fun VitrViewModel.moveQueueEntry(
    fromIndex: Int,
    toIndex: Int
) {
    PlaybackQueueStore.move(
        fromIndex,
        toIndex
    )
}

fun VitrViewModel.clearPlaybackQueue() {
    PlaybackQueueStore.clear()
}

fun VitrViewModel.queueNext() {
    val player = playerState.value

    PlaybackQueueStore.advance(
        repeatMode = player.repeatMode.toQueueRepeatMode(),
        shuffle = player.shuffleEnabled
    )
}

fun VitrViewModel.queuePrevious() {
    PlaybackQueueStore.previous(
        playerState.value.repeatMode
            .toQueueRepeatMode()
    )
}

fun VitrViewModel.playlistRepository():
    PlaylistRepository =
    PlaylistRepository(
        getApplication<Application>()
    )

fun VitrViewModel.createPlaylist(
    name: String,
    onCreated: (Long) -> Unit = {}
) {
    viewModelScope.launch {
        val id = withContext(Dispatchers.IO) {
            playlistRepository().create(name)
        }
        onCreated(id)
    }
}

fun VitrViewModel.renamePlaylist(
    playlistId: Long,
    name: String
) {
    viewModelScope.launch(Dispatchers.IO) {
        playlistRepository().rename(
            playlistId,
            name
        )
    }
}

fun VitrViewModel.deletePlaylist(
    playlistId: Long
) {
    viewModelScope.launch(Dispatchers.IO) {
        playlistRepository().delete(playlistId)
    }
}

fun VitrViewModel.addTrackToPlaylist(
    playlistId: Long,
    track: Track
) {
    viewModelScope.launch(Dispatchers.IO) {
        playlistRepository().addTrack(
            playlistId,
            track
        )
    }
}

fun VitrViewModel.removeTrackFromPlaylist(
    playlistId: Long,
    rowId: Long
) {
    viewModelScope.launch(Dispatchers.IO) {
        playlistRepository().removeTrack(
            playlistId,
            rowId
        )
    }
}

fun VitrViewModel.playPlaylist(
    playlistId: Long
) {
    viewModelScope.launch {
        val tracks = withContext(Dispatchers.IO) {
            playlistRepository().trackList(playlistId)
        }

        tracks.firstOrNull()?.let { first ->
            playQueued(
                first,
                tracks
            )
        }
    }
}

fun VitrViewModel.addPlaylistToQueue(
    playlistId: Long
) {
    viewModelScope.launch {
        val tracks = withContext(Dispatchers.IO) {
            playlistRepository().trackList(playlistId)
        }

        PlaybackQueueStore.appendAll(tracks)
    }
}

fun VitrViewModel.downloadPlaylist(
    playlistId: Long
) {
    val app = getApplication<Application>()

    viewModelScope.launch(Dispatchers.IO) {
        val tracks = playlistRepository()
            .trackList(playlistId)

        val format = SaveFormat.MP3
        val quality = defaultQualityFor(format)

        tracks.forEach { track ->
            val source = automaticDownloadSource(
                track.downloadUrl,
                track.streamUrl
            ) ?: return@forEach

            VitrDownloadService.enqueue(
                app,
                SaveRequest(
                    sourceUrl = source,
                    title = track.title,
                    artist = track.artist,
                    format = format,
                    quality = quality
                )
            )
        }
    }
}

private fun VitrRepeatMode.toQueueRepeatMode():
    QueueRepeatMode =
    when (this) {
        VitrRepeatMode.All -> QueueRepeatMode.All
        VitrRepeatMode.One -> QueueRepeatMode.One
        VitrRepeatMode.Off -> QueueRepeatMode.Off
    }
