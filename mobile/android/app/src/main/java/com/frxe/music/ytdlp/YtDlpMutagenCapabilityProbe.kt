package com.frxe.music.ytdlp

import java.io.File

object YtDlpMutagenCapabilityProbe {
    fun isAvailable(noBackupFilesDir: File): Boolean {
        val pythonLibRoot = File(
            noBackupFilesDir,
            "youtubedl-android/packages/python/usr/lib"
        )

        return pythonLibRoot
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .filter { it.name.startsWith("python") }
            .map { File(it, "site-packages/mutagen") }
            .any(File::isDirectory)
    }
}
