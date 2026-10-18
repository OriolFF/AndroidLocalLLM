package com.llmlocal.feature.webclipper.mvi

/**
 * One-shot side effects emitted by the browser ViewModel. Consumed in
 * [com.llmlocal.feature.webclipper.WebClipperBrowserRoute] via a
 * `Channel(BUFFERED)` / `receiveAsFlow()`.
 */
sealed interface BrowserEffect {
    data class NavigateToExtraction(val candidateId: String) : BrowserEffect
    data object NavigateToModelManagement : BrowserEffect
    data class ShowSnackbar(val message: String) : BrowserEffect
    data class LoadUrl(val url: String) : BrowserEffect
    data class ShareText(val text: String) : BrowserEffect
}
