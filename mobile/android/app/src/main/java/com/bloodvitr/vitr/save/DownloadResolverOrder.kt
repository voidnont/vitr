package com.bloodvitr.vitr.save

enum class DownloadResolverKind {
    InnerTube,
    NewPipe,
    YtDlp
}

fun downloadResolverOrder(): List<DownloadResolverKind> = listOf(
    DownloadResolverKind.InnerTube,
    DownloadResolverKind.NewPipe,
    DownloadResolverKind.YtDlp
)
