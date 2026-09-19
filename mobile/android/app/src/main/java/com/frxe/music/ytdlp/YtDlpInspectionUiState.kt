package com.frxe.music.ytdlp

sealed interface YtDlpInspectionUiState {
    data object Idle : YtDlpInspectionUiState

    data class Loading(
        val url: String
    ) : YtDlpInspectionUiState

    data class Success(
        val url: String,
        val inspection: YtDlpInspection
    ) : YtDlpInspectionUiState

    data class Failure(
        val url: String?,
        val message: String
    ) : YtDlpInspectionUiState
}
