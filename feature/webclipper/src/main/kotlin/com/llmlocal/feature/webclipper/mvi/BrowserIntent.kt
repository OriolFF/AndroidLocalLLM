package com.llmlocal.feature.webclipper.mvi

/** User and WebView events handled by the browser ViewModel. */
sealed interface BrowserIntent {
    data class UpdateDraftUrl(val value: String) : BrowserIntent
    data class UrlFocusChanged(val focused: Boolean) : BrowserIntent
    data object SubmitUrl : BrowserIntent
    data object Reload : BrowserIntent
    data object Stop : BrowserIntent
    data object GoForward : BrowserIntent

    /** WebView `onPageStarted`; also sent when the screen attaches to a WebView that already shows a page. */
    data class PageStarted(val url: String) : BrowserIntent
    data class PageProgress(val progress: Int) : BrowserIntent
    data class PageFinished(val url: String, val title: String?) : BrowserIntent

    /** WebView `doUpdateVisitedHistory`. */
    data class HistoryChanged(val canGoBack: Boolean, val canGoForward: Boolean) : BrowserIntent

    /** Main-frame load error (`WebViewClient.onReceivedError`). */
    data object PageLoadFailed : BrowserIntent
    data class Scrolled(val deltaY: Int) : BrowserIntent
    data object Scan : BrowserIntent
    data class PickCandidate(val candidateId: String) : BrowserIntent
    data object ExtractWholePage : BrowserIntent
    data object DismissSheet : BrowserIntent
    data object OpenModelManagement : BrowserIntent
    data object SharePage : BrowserIntent
    data object CopyLink : BrowserIntent
    data object OpenExternal : BrowserIntent
}
