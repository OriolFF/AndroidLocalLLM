package com.llmlocal.feature.recipe.mvi

/**
 * State of the on-device LLM model — used to drive the recipe screen's
 * download / ready / missing-model UI.
 *
 *  - [NoModelAvailable]   — no model is installed on disk; the banner
 *    prompts the user to open the model management screen.
 *  - [Unknown]            — initial state before we've checked the disk.
 *  - [Checking]           — checking whether the model file is already present.
 *  - [Downloading]        — the download is in its "starting" phase.
 *  - [DownloadProgress]   — concrete progress: bytes / total / speed / ETA.
 *  - [Ready]              — model is on disk and the engine initialized.
 *  - [Failed]             — last attempt failed; carries a user-facing reason.
 */
sealed interface ModelStatus {
    data object NoModelAvailable : ModelStatus
    data object Unknown : ModelStatus
    data object Checking : ModelStatus
    data object Downloading : ModelStatus
    data class DownloadProgress(
        val bytesRead: Long,
        val totalBytes: Long?,
        val percent: Int,
        val speedBytesPerSec: Long,
        val etaSeconds: Long?,
    ) : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val reason: String) : ModelStatus
}