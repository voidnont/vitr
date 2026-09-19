package com.bloodvitr.vitr

import android.app.Application
import com.bloodvitr.vitr.downloads.DownloadSupport
import com.bloodvitr.vitr.playback.PlaybackPrefetcher
import com.bloodvitr.vitr.playback.PlaybackQueueStore
import com.bloodvitr.vitr.save.DownloadQueueStore
import com.bloodvitr.vitr.source.DownloadedTrackRegistry
import com.bloodvitr.vitr.source.VitrNewPipeRuntime
import com.bloodvitr.vitr.source.ResolverDiagnosticsStore
import com.bloodvitr.vitr.source.YouTubeAudioResolverRuntime
import com.bloodvitr.vitr.updates.RuntimeHealthStore
import com.bloodvitr.vitr.updates.YtDlpRuntimeUpdater
import com.bloodvitr.vitr.ytdlp.YtDlpCore

class VitrApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        VitrNewPipeRuntime.initialize()
        ResolverDiagnosticsStore.initialize(this)
        DownloadedTrackRegistry.initialize(this)
        RuntimeHealthStore.initialize(this)
        YtDlpCore.initialize(this)
        YouTubeAudioResolverRuntime.initialize(this)
        YtDlpRuntimeUpdater.initializeAndSchedule(this)

        DownloadSupport.initialize(this)
        DownloadQueueStore.initialize(this)
        PlaybackQueueStore.initialize(this)
        PlaybackPrefetcher.initialize()
    }
}
