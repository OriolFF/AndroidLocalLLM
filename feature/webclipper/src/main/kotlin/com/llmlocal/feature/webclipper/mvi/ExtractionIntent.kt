package com.llmlocal.feature.webclipper.mvi

/** User events handled by the extraction ViewModel. */
sealed interface ExtractionIntent {
    data object Start : ExtractionIntent
    data object Cancel : ExtractionIntent
    data object Retry : ExtractionIntent
    data object RetryWholePage : ExtractionIntent
    data class TogglePicture(val url: String) : ExtractionIntent
    data object ToggleRaw : ExtractionIntent
    data object BackToCandidates : ExtractionIntent

    /** Duplicate warning: overwrite the previously saved item (same id). */
    data object ReplaceExisting : ExtractionIntent

    /** Duplicate warning: save as a new item next to the old one. */
    data object KeepBoth : ExtractionIntent
}
