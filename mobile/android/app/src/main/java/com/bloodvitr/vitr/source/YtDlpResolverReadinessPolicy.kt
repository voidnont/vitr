package com.bloodvitr.vitr.source

internal object YtDlpResolverReadinessPolicy {
    fun shouldInitialize(runtimeReady: Boolean): Boolean = !runtimeReady
}
