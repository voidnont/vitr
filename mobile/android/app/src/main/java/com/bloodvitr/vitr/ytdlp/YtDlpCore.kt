package com.bloodvitr.vitr.ytdlp

import android.content.Context
import com.bloodvitr.vitr.intake.UrlIntakeParser
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YtDlpCore {
    @Volatile private var currentCapabilities = YtDlpCoreCapabilities()
    val capabilities: YtDlpCoreCapabilities get() = currentCapabilities

    @Synchronized
    fun initialize(context: Context): YtDlpCoreCapabilities {
        val appContext = context.applicationContext
        var initializedCapabilities = YtDlpCoreInitializationPolicy.initialize(
            initializeYtDlp = {
                YtDlpRuntimeBootstrap.initializeWithRecovery(
                    initialize = {
                        YoutubeDL.getInstance().init(appContext)
                    },
                    cleanup = {
                        File(
                            appContext.noBackupFilesDir,
                            YoutubeDL.baseName
                        ).deleteRecursively()
                    }
                ).getOrThrow()
            },
            initializeFFmpeg = { FFmpeg.getInstance().init(appContext) },
            initializeAria2c = { Aria2c.getInstance().init(appContext) }
        )

        if (initializedCapabilities.ytDlpReady) {
            initializedCapabilities = initializedCapabilities.withCapability(
                YtDlpCoreCapability.Mutagen,
                YtDlpMutagenCapabilityProbe.isAvailable(appContext.noBackupFilesDir)
            )
        }

        currentCapabilities = initializedCapabilities
        return initializedCapabilities
    }

    fun newInspectionProcessId(): String = "vitr-inspect-${UUID.randomUUID()}"
    fun newDownloadProcessId(): String = "vitr-download-${UUID.randomUUID()}"

    suspend fun inspect(url: String, processId: String = newInspectionProcessId()): YtDlpInspectionResult {
        val normalizedUrl = UrlIntakeParser.extractFirstHttpUrl(url)
            ?: return YtDlpInspectionResult.Failure("Enter a valid http or https link.")
        if (!capabilities.ytDlpReady) return YtDlpInspectionResult.Failure("Link inspection is unavailable right now.")
        return try {
            val inspection = withContext(Dispatchers.IO) {
                val request = YoutubeDLRequest(normalizedUrl)
                YtDlpInspectCommandPolicy.arguments().forEach { request.addOption(it) }
                val response = YoutubeDL.getInstance().execute(request = request, processId = processId)
                YtDlpInspectionParser.parse(response.out)
            }
            YtDlpInspectionResult.Success(inspection)
        } catch (_: YoutubeDL.CanceledException) {
            YtDlpInspectionResult.Failure("Inspection cancelled")
        } catch (_: InterruptedException) {
            YtDlpInspectionResult.Failure("Inspection cancelled")
        } catch (cancelled: CancellationException) {
            cancelInspection(processId)
            throw cancelled
        } catch (_: Throwable) {
            YtDlpInspectionResult.Failure("Could not inspect this link.")
        }
    }

    suspend fun download(
        request: YtDlpDownloadRequest,
        workDir: File,
        processId: String = newDownloadProcessId(),
        onProgress: (Float) -> Unit = {}
    ): YtDlpDownloadResult = YtDlpDownloadExecutor.download(
        request = request,
        capabilities = capabilities,
        workDir = workDir,
        processId = processId,
        onProgress = onProgress
    )

    fun cancelInspection(processId: String): Boolean = YoutubeDL.getInstance().destroyProcessById(processId)
    fun cancelDownload(processId: String): Boolean = YtDlpDownloadExecutor.cancel(processId)
}
