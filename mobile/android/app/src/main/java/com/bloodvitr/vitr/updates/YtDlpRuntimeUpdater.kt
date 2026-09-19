package com.bloodvitr.vitr.updates

import android.content.Context
import com.bloodvitr.vitr.source.YtDlpPlaybackRecoveryPolicy
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object YtDlpRuntimeUpdater {
    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    private val updateMutex =
        Mutex()

    private val playbackRecoveryMutex =
        Mutex()

    @Volatile
    private var appContext: Context? = null

    private var lastPlaybackRecoveryAttemptMs: Long? = null

    fun initializeAndSchedule(
        context: Context
    ) {
        val appContext =
            context.applicationContext

        this.appContext = appContext

        RuntimeHealthStore.initialize(
            appContext
        )

        runCatching {
            YoutubeDL
                .getInstance()
                .init(appContext)

            installedVersion(
                appContext
            )
        }
            .fold(
                onSuccess = { version ->
                    RuntimeHealthStore
                        .recordYtDlpInit(
                            version = version,
                            error = null
                        )
                },
                onFailure = { error ->
                    RuntimeHealthStore
                        .recordYtDlpInit(
                            version = null,
                            error = error.message
                        )
                }
            )

        scope.launch {
            updateIfDue(
                context = appContext,
                force = false
            )
        }
    }

    suspend fun updateNow(
        context: Context
    ): RuntimeHealthState =
        withContext(
            Dispatchers.IO
        ) {
            updateIfDue(
                context =
                    context.applicationContext,
                force = true
            )

            RuntimeHealthStore
                .state
                .value
        }

    suspend fun recoverForPlayback(): Boolean =
        playbackRecoveryMutex.withLock {
            val context = appContext
                ?: return@withLock false
            val now = System.currentTimeMillis()

            if (
                !YtDlpPlaybackRecoveryPolicy.shouldAttempt(
                    lastAttemptAtMs = lastPlaybackRecoveryAttemptMs,
                    nowMs = now
                )
            ) {
                return@withLock false
            }

            lastPlaybackRecoveryAttemptMs = now
            updateIfDue(
                context = context,
                force = true
            )
            true
        }

    private suspend fun updateIfDue(
        context: Context,
        force: Boolean
    ) {
        updateMutex.withLock {
            val now =
                System.currentTimeMillis()

            val lastAttempt =
                RuntimeHealthStore
                    .state
                    .value
                    .lastYtDlpUpdateAttemptMs

            if (
                !force &&
                !RuntimeUpdatePolicy.shouldCheck(
                    lastAttempt,
                    now
                )
            ) {
                return
            }

            RuntimeHealthStore
                .recordYtDlpUpdateAttempt(
                    now
                )

            runCatching {
                val instance =
                    YoutubeDL.getInstance()

                instance.init(context)

                val status =
                    instance.updateYoutubeDL(
                        context,
                        YoutubeDL.UpdateChannel.STABLE
                    )

                val version =
                    installedVersion(context)

                status to version
            }
                .fold(
                    onSuccess = {
                            (status, version) ->

                        RuntimeHealthStore
                            .recordYtDlpInit(
                                version = version,
                                error = null
                            )

                        RuntimeHealthStore
                            .recordYtDlpUpdateResult(
                                version = version,
                                status =
                                    status?.name
                                        ?: "Already current",
                                error = null
                            )
                    },
                    onFailure = { error ->
                        RuntimeHealthStore
                            .recordYtDlpUpdateResult(
                                version =
                                    RuntimeHealthStore
                                        .state
                                        .value
                                        .ytDlpVersion,
                                status = null,
                                error = error.message
                            )
                    }
                )
        }
    }

    private fun installedVersion(
        context: Context
    ): String =
        YoutubeDL
            .getInstance()
            .versionName(context)
            ?: YoutubeDL
                .getInstance()
                .version(context)
            ?: "unknown"
}
