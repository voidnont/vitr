package com.frxe.music.ui

import androidx.lifecycle.viewModelScope
import com.frxe.music.intake.UrlIntakeParser
import com.frxe.music.ytdlp.YtDlpCore
import com.frxe.music.ytdlp.YtDlpInspectionResult
import com.frxe.music.ytdlp.YtDlpInspectionUiState
import java.util.WeakHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private class FrxeYtDlpUiHolder {
    val pendingExternalUrl =
        MutableStateFlow<String?>(null)

    val inspectionState =
        MutableStateFlow<YtDlpInspectionUiState>(
            YtDlpInspectionUiState.Idle
        )

    var inspectionProcessId: String? = null
    var inspectionJob: Job? = null
}

private val frxeYtDlpUiHolders =
    WeakHashMap<FrxeViewModel, FrxeYtDlpUiHolder>()

private fun FrxeViewModel.ytDlpUiHolder(): FrxeYtDlpUiHolder =
    synchronized(frxeYtDlpUiHolders) {
        frxeYtDlpUiHolders.getOrPut(this) {
            FrxeYtDlpUiHolder()
        }
    }

val FrxeViewModel.pendingExternalUrl:
    StateFlow<String?>
    get() =
        ytDlpUiHolder()
            .pendingExternalUrl
            .asStateFlow()

val FrxeViewModel.inspectionState:
    StateFlow<YtDlpInspectionUiState>
    get() =
        ytDlpUiHolder()
            .inspectionState
            .asStateFlow()

fun FrxeViewModel.acceptSharedText(
    text: String?
) {
    val url =
        UrlIntakeParser.extractFirstHttpUrl(text)
            ?: return

    ytDlpUiHolder()
        .pendingExternalUrl
        .value = url
}

fun FrxeViewModel.consumeExternalUrl() {
    ytDlpUiHolder()
        .pendingExternalUrl
        .value = null
}

fun FrxeViewModel.inspectUrl(
    url: String
) {
    val holder =
        ytDlpUiHolder()

    val normalizedUrl =
        UrlIntakeParser.extractFirstHttpUrl(url)

    if (normalizedUrl == null) {
        holder.inspectionState.value =
            YtDlpInspectionUiState.Failure(
                url = null,
                message = "Enter a valid http or https link."
            )
        return
    }

    holder.inspectionProcessId
        ?.let(YtDlpCore::cancelInspection)
    holder.inspectionJob?.cancel()

    val processId =
        YtDlpCore.newInspectionProcessId()

    holder.inspectionProcessId =
        processId

    holder.inspectionState.value =
        YtDlpInspectionUiState.Loading(
            normalizedUrl
        )

    val job =
        viewModelScope.launch {
            val result =
                YtDlpCore.inspect(
                    url = normalizedUrl,
                    processId = processId
                )

            if (
                holder.inspectionProcessId !=
                processId
            ) {
                return@launch
            }

            holder.inspectionState.value =
                when (result) {
                    is YtDlpInspectionResult.Success ->
                        YtDlpInspectionUiState.Success(
                            url = normalizedUrl,
                            inspection = result.inspection
                        )

                    is YtDlpInspectionResult.Failure ->
                        YtDlpInspectionUiState.Failure(
                            url = normalizedUrl,
                            message = result.message
                        )
                }
        }

    holder.inspectionJob =
        job

    job.invokeOnCompletion {
        synchronized(holder) {
            if (
                holder.inspectionJob ===
                job
            ) {
                holder.inspectionJob =
                    null
            }

            if (
                holder.inspectionProcessId ==
                processId
            ) {
                holder.inspectionProcessId =
                    null
            }
        }
    }
}

fun FrxeViewModel.clearInspection() {
    val holder =
        ytDlpUiHolder()

    holder.inspectionProcessId
        ?.let(YtDlpCore::cancelInspection)
    holder.inspectionProcessId =
        null

    holder.inspectionJob?.cancel()
    holder.inspectionJob =
        null

    holder.inspectionState.value =
        YtDlpInspectionUiState.Idle
}
