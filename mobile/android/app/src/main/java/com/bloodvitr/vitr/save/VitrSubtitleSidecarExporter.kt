package com.bloodvitr.vitr.save

import android.content.Context
import android.os.Environment
import java.io.File

class VitrSubtitleSidecarExporter(context: Context) {
    private val appContext = context.applicationContext

    fun export(files: List<File>, title: String): List<File> {
        val subtitleFiles = SubtitleSidecarPolicy.subtitleFiles(files)
        if (subtitleFiles.isEmpty()) return emptyList()

        val root = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: appContext.filesDir
        val directory = File(root, "Vitr/Subtitles").apply { mkdirs() }

        return subtitleFiles.map { source ->
            val destination = File(
                directory,
                SubtitleSidecarPolicy.destinationName(title, source)
            )
            source.copyTo(destination, overwrite = true)
            destination
        }
    }
}
