package com.bloodvitr.vitr.ytdlp

import com.bloodvitr.vitr.intake.UrlIntakeParser
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object YtDlpDownloadExecutor {

    suspend fun download(
        request: YtDlpDownloadRequest,
        capabilities: YtDlpCoreCapabilities,
        workDir: File,
        processId: String,
        onProgress: (Float) -> Unit
    ): YtDlpDownloadResult {
        val normalizedUrl =
            UrlIntakeParser.extractFirstHttpUrl(request.sourceUrl)
                ?: throw IllegalArgumentException(
                    "Enter a valid http or https link."
                )

        if (!capabilities.ytDlpReady) {
            throw IllegalStateException(
                "Download engine is unavailable right now."
            )
        }

        val normalizedRequest =
            request.copy(sourceUrl = normalizedUrl)

        return withContext(Dispatchers.IO) {
            prepareWorkDirectory(workDir)

            val useAria2c =
                YtDlpAria2Policy.shouldUse(
                    normalizedRequest,
                    capabilities
                )

            try {
                executeAttempt(
                    request = normalizedRequest,
                    workDir = workDir,
                    processId = processId,
                    useAria2c = useAria2c,
                    onProgress = onProgress
                )
            } catch (cancelled: CancellationException) {
                cancel(processId)
                clearWorkDirectory(workDir)
                throw cancelled
            } catch (_: YoutubeDL.CanceledException) {
                clearWorkDirectory(workDir)
                throw CancellationException("Download cancelled")
            } catch (_: InterruptedException) {
                cancel(processId)
                clearWorkDirectory(workDir)
                throw CancellationException("Download cancelled")
            } catch (firstError: Throwable) {
                findResult(
                    workDir = workDir,
                    usedAcceleratedDownloader = useAria2c
                )?.let { return@withContext it }

                if (!useAria2c) {
                    clearWorkDirectory(workDir)
                    throw IllegalStateException(
                        "Download failed.",
                        firstError
                    )
                }

                clearWorkDirectory(workDir)

                try {
                    executeAttempt(
                        request = normalizedRequest,
                        workDir = workDir,
                        processId = processId,
                        useAria2c = false,
                        onProgress = onProgress
                    )
                } catch (cancelled: CancellationException) {
                    cancel(processId)
                    clearWorkDirectory(workDir)
                    throw cancelled
                } catch (_: YoutubeDL.CanceledException) {
                    clearWorkDirectory(workDir)
                    throw CancellationException("Download cancelled")
                } catch (_: InterruptedException) {
                    cancel(processId)
                    clearWorkDirectory(workDir)
                    throw CancellationException("Download cancelled")
                } catch (retryError: Throwable) {
                    findResult(
                        workDir = workDir,
                        usedAcceleratedDownloader = false
                    )?.let { return@withContext it }

                    clearWorkDirectory(workDir)
                    throw IllegalStateException(
                        "Download failed.",
                        retryError
                    )
                }
            }
        }
    }

    fun cancel(processId: String): Boolean =
        YoutubeDL
            .getInstance()
            .destroyProcessById(processId)

    private fun executeAttempt(
        request: YtDlpDownloadRequest,
        workDir: File,
        processId: String,
        useAria2c: Boolean,
        onProgress: (Float) -> Unit
    ): YtDlpDownloadResult {
        val outputTemplate =
            File(workDir, "media.%(ext)s")
                .absolutePath

        val youtubeDlRequest =
            YoutubeDLRequest(request.sourceUrl)

        YtDlpDownloadCommandPolicy
            .arguments(
                request = request,
                outputTemplate = outputTemplate,
                useAria2c = useAria2c
            )
            .forEach { option ->
                if (option.argument == null) {
                    youtubeDlRequest.addOption(option.option)
                } else {
                    youtubeDlRequest.addOption(
                        option.option,
                        option.argument
                    )
                }
            }

        YoutubeDL
            .getInstance()
            .execute(
                request = youtubeDlRequest,
                processId = processId,
                callback = { percent, _, _ ->
                    if (percent >= 0f) {
                        onProgress(
                            (percent / 100f)
                                .coerceIn(0f, 1f)
                        )
                    }
                }
            )

        return findResult(
            workDir = workDir,
            usedAcceleratedDownloader = useAria2c
        ) ?: throw IllegalStateException(
            "Download produced no usable media file."
        )
    }

    private fun prepareWorkDirectory(workDir: File) {
        if (workDir.exists() && !workDir.isDirectory) {
            throw IllegalStateException(
                "Download workspace is unavailable."
            )
        }

        if (!workDir.exists() && !workDir.mkdirs()) {
            throw IllegalStateException(
                "Download workspace is unavailable."
            )
        }
    }

    private fun findResult(
        workDir: File,
        usedAcceleratedDownloader: Boolean
    ): YtDlpDownloadResult? {
        val root =
            runCatching { workDir.canonicalFile }
                .getOrNull()
                ?: return null

        val files =
            workDir
                .walkTopDown()
                .filter(File::isFile)
                .mapNotNull { file ->
                    runCatching { file.canonicalFile }
                        .getOrNull()
                }
                .filter { file ->
                    file.length() > 0L &&
                        isInside(root, file)
                }
                .toList()

        val sidecars =
            files.filter(::isSidecarFile)

        val media =
            files
                .asSequence()
                .filterNot(::isSidecarFile)
                .filterNot(::isPartialFile)
                .maxWithOrNull(
                    compareBy<File> { it.length() }
                        .thenBy { it.lastModified() }
                )
                ?: return null

        return YtDlpDownloadResult(
            mediaFile = media,
            sidecarFiles = sidecars,
            usedAcceleratedDownloader = usedAcceleratedDownloader
        )
    }

    private fun clearWorkDirectory(workDir: File) {
        if (!workDir.exists() || !workDir.isDirectory) return

        workDir.listFiles()
            ?.forEach { child ->
                runCatching { child.deleteRecursively() }
            }
    }

    private fun isInside(root: File, candidate: File): Boolean {
        val prefix =
            root.path.trimEnd(File.separatorChar) +
                File.separator

        return candidate == root ||
            candidate.path.startsWith(prefix)
    }

    private fun isPartialFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".part") ||
            name.endsWith(".ytdl") ||
            name.endsWith(".tmp")
    }

    private fun isSidecarFile(file: File): Boolean =
        file.extension.lowercase() in SIDE_CAR_EXTENSIONS

    private val SIDE_CAR_EXTENSIONS =
        setOf(
            "vtt",
            "srt",
            "ass",
            "lrc",
            "json",
            "jpg",
            "jpeg",
            "png",
            "webp",
            "description"
        )
}
