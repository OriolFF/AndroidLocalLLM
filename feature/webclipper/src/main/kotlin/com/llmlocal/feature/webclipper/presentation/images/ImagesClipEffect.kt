package com.llmlocal.feature.webclipper.presentation.images

/** One-shot events of the images screen. */
sealed interface ImagesClipEffect {
    data object LaunchPicker : ImagesClipEffect
    data class LaunchPickerFor(val remaining: Int) : ImagesClipEffect
    data class NavigateToExtraction(val candidateId: String) : ImagesClipEffect
    data object Closed : ImagesClipEffect
    data object NavigateToModelManagement : ImagesClipEffect
    data class Notify(val kind: ImagesNotice) : ImagesClipEffect
}

/** Snackbar kinds; the route maps them to labels (rule 0). */
enum class ImagesNotice { SKIPPED, CAPPED }
