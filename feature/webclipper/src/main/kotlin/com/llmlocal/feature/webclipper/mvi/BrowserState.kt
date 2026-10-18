package com.llmlocal.feature.webclipper.mvi

import androidx.compose.runtime.Immutable
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.feature.webclipper.ClipCandidate
import com.llmlocal.feature.webclipper.readiness.ModelReadiness

/**
 * Single source of truth for the browser screen (URL bar, WebView, clip
 * button, candidate sheet).
 *
 * @property url the URL the WebView currently shows
 * @property draftUrl text in the address bar while editing
 * @property urlFocused whether the address bar has focus
 * @property pageProgress 0–100 from `onProgressChanged`
 * @property fab what the clip button shows and does
 * @property fabExpanded extended (label visible) vs. collapsed on scroll
 * @property candidates what the last scan found on this page
 * @property sheet which bottom sheet is open
 * @property readiness model readiness (drives [ClipFabState.ModelNotReady])
 */
@Immutable
data class BrowserState(
    val url: String = "",
    val draftUrl: String = "",
    val urlFocused: Boolean = false,
    val pageTitle: String? = null,
    val pageProgress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val fab: ClipFabState = ClipFabState.PageLoading,
    val fabExpanded: Boolean = true,
    val candidates: List<ClipCandidate> = emptyList(),
    val sheet: BrowserSheet = BrowserSheet.None,
    // TEMP shim type: swap the import for WS3's `com.llmlocal.core.llm.readiness.ModelReadiness` at merge.
    val readiness: ModelReadiness = ModelReadiness.Checking,
)

/** What the clip button renders; see UX spec §2 for icon / label / tap per state. */
sealed interface ClipFabState {
    /** No model ready; [download] is non-null while one is downloading. Tap → model management. */
    data class ModelNotReady(val download: PerModelProgress? = null) : ClipFabState

    /** Page still loading; disabled. */
    data object PageLoading : ClipFabState

    /** Ready to scan. */
    data object Idle : ClipFabState

    /** Capture + detect in flight (< 1 s). */
    data object Scanning : ClipFabState

    /** [count] candidates found; 1 → extraction, more → candidate sheet. */
    data class Found(val count: Int) : ClipFabState

    /** An extraction is running for this page; tap returns to it. */
    data object Extracting : ClipFabState
}

/** Bottom sheet shown over the browser. */
enum class BrowserSheet { None, Candidates, NothingDetected }
