package com.llmlocal.core.llm.readiness

import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.model.LlmModelDescriptor

/**
 * Whether the currently selected model is ready to run on this device.
 *
 * Produced by [ModelReadinessObserver]; consumed by any ViewModel that
 * gates an LLM action on a model being present and loaded (the generic
 * LLM-task screen, the Recipe Keeper clip FAB, …). Consumers map it to
 * whatever screen-facing status type they already have — the LLM-task
 * feature maps it to `ModelStatus`.
 *
 * Typical sequence on a fresh install, default model selected:
 *
 * ```
 *   Checking → NoModel                       (file not on disk yet)
 *   Downloading(progress) × n                (worker streaming bytes)
 *   Checking → Initializing → Ready          (SUCCEEDED written, engine loaded)
 * ```
 *
 *  - [Checking]      — resolving the selection and looking at the disk.
 *                      Also the initial value before anything is known.
 *  - [NoModel]       — the selected id is not in the catalog, or its file
 *                      is not installed. The user must visit the model
 *                      screen.
 *  - [Downloading]   — the download worker is streaming bytes for the
 *                      selected model; [Downloading.progress] is live.
 *  - [Initializing]  — the file is on disk and the engine is loading it.
 *  - [Ready]         — the engine initialised successfully for
 *                      [Ready.descriptor].
 *  - [Failed]        — the engine could not initialise. [Failed.reason] is
 *                      user-facing; [Failed.descriptor] is the model that
 *                      failed.
 */
sealed interface ModelReadiness {
    data object Checking : ModelReadiness
    data object NoModel : ModelReadiness
    data class Downloading(val progress: PerModelProgress) : ModelReadiness
    data object Initializing : ModelReadiness
    data class Ready(val descriptor: LlmModelDescriptor) : ModelReadiness
    data class Failed(
        val reason: String,
        val descriptor: LlmModelDescriptor? = null,
    ) : ModelReadiness
}
