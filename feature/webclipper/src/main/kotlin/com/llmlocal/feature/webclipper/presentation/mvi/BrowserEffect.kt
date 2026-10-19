package com.llmlocal.feature.webclipper.presentation.mvi

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

    /** WebView commands the route applies to the shared instance. */
    data object ReloadPage : BrowserEffect
    data object StopLoading : BrowserEffect
    data object GoForward : BrowserEffect

    /** Open [url] in the system browser. */
    data class OpenExternal(val url: String) : BrowserEffect

    /** Put [text] on the clipboard (the route shows [BrowserEffect.ShowSnackbar] separately). */
    data class CopyToClipboard(val text: String) : BrowserEffect
}
