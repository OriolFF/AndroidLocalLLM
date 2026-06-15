package com.llmlocal.feature.recipe.mvi

/**
 * State of the on-device LLM model — used to drive the download / ready UI.
 */
sealed interface ModelStatus {
    data object Unknown : ModelStatus
    data object Checking : ModelStatus
    data object Downloading : ModelStatus
    data class DownloadProgress(val percent: Int) : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val reason: String) : ModelStatus
}
