package com.llmlocal.feature.webclipper.mvi

/**
 * One-shot side effects emitted by the extraction ViewModel. Consumed in
 * [com.llmlocal.feature.webclipper.WebClipperExtractionRoute].
 */
sealed interface ExtractionEffect {
    /** Item saved; the route forwards [id] to `onSaved`. */
    data class NavigateToSaved(val id: String) : ExtractionEffect

    /** Cancelled or "back to page"; the route calls `onCancelled`. */
    data object PopToBrowser : ExtractionEffect

    data class ShowSnackbar(val message: String) : ExtractionEffect
}
