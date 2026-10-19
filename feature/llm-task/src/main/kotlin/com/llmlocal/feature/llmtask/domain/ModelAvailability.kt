package com.llmlocal.feature.llmtask.domain

/**
 * Feature-domain view of "is the selected model ready to run?". Mirrors the
 * shared `ModelReadiness` of `:core:llm` without depending on it; the
 * `data` adapter maps one to the other.
 *
 *  - [Downloading] is kept distinct so a presenter may choose to show it,
 *    even though the task screen currently folds it into "no model".
 *  - [Failed.installed] is true when the model file was on disk but the
 *    engine could not load it.
 */
sealed interface ModelAvailability {
    data object Checking : ModelAvailability
    data object NoModel : ModelAvailability
    data object Downloading : ModelAvailability
    data object Initializing : ModelAvailability
    data object Ready : ModelAvailability
    data class Failed(val reason: String, val installed: Boolean) : ModelAvailability
}
