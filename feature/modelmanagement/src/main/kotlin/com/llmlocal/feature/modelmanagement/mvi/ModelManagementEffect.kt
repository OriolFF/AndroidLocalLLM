package com.llmlocal.feature.modelmanagement.mvi

/**
 * One-shot side effects emitted by the model management ViewModel.
 *
 * Effects are dispatched through a `Channel(BUFFERED)` and consumed in
 * `Route` via `LaunchedEffect`.
 */
sealed interface ModelManagementEffect {
    /** Show a transient snackbar with [message]. */
    data class ShowSnackbar(val message: String) : ModelManagementEffect
}