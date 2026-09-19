package com.frxe.music.source

internal object YtDlpResolverReadinessPolicy {
    fun shouldInitialize(runtimeReady: Boolean): Boolean = !runtimeReady
}
