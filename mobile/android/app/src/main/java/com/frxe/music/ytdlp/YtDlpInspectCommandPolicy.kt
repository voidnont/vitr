package com.frxe.music.ytdlp

object YtDlpInspectCommandPolicy {
    fun arguments(): List<String> =
        listOf(
            "--dump-single-json",
            "--flat-playlist",
            "--skip-download",
            "--no-warnings",
            "--ignore-config"
        )
}
