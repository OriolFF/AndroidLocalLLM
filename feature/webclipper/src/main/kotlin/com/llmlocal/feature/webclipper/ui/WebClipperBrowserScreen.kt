package com.llmlocal.feature.webclipper.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import com.llmlocal.feature.webclipper.WebClipperLabels
import com.llmlocal.feature.webclipper.hostOf
import com.llmlocal.feature.webclipper.mvi.BrowserIntent
import com.llmlocal.feature.webclipper.mvi.BrowserSheet
import com.llmlocal.feature.webclipper.mvi.BrowserState
import com.llmlocal.feature.webclipper.mvi.ClipFabState

/**
 * Stateless browser screen (UX spec §2): own `Scaffold` with
 * [BrowserTopBar], the WebView slot, the [ClipFab] and the two sheets.
 *
 * Insets: `contentWindowInsets = safeDrawing` so the WebView never renders
 * under the gesture bar or the keyboard; only the FAB floats, and it is
 * hidden while the IME is visible. The WebView slot is a traversal group
 * so TalkBack reads the page as one region after the clip button.
 *
 * @param webView the `AndroidView` slot; receives the modifier to fill the
 *   content area
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebClipperBrowserScreen(
    state: BrowserState,
    labels: WebClipperLabels,
    onIntent: (BrowserIntent) -> Unit,
    onBackToLibrary: () -> Unit,
    snackbarHostState: SnackbarHostState,
    webView: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imeVisible = WindowInsets.isImeVisible
    val showFab = state.url.isNotBlank() && !imeVisible

    // Blank start: put the caret in the address bar straight away.
    LaunchedEffect(Unit) {
        if (state.url.isBlank() && !state.urlFocused) onIntent(BrowserIntent.UrlFocusChanged(true))
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            BrowserTopBar(
                state = state,
                labels = labels,
                onIntent = onIntent,
                onBackToLibrary = onBackToLibrary,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (showFab) {
                ClipFab(
                    state = state.fab,
                    expanded = state.fabExpanded,
                    labels = labels,
                    onClick = {
                        when (state.fab) {
                            is ClipFabState.ModelNotReady -> onIntent(BrowserIntent.OpenModelManagement)
                            else -> onIntent(BrowserIntent.Scan)
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .semantics { isTraversalGroup = true },
        ) {
            webView(Modifier.fillMaxSize())
        }
    }

    when (state.sheet) {
        BrowserSheet.None -> Unit
        BrowserSheet.Candidates -> CandidateSheet(
            candidates = state.candidates,
            host = hostOf(state.url) ?: state.url,
            labels = labels,
            onPick = { onIntent(BrowserIntent.PickCandidate(it)) },
            onDismiss = { onIntent(BrowserIntent.DismissSheet) },
        )
        BrowserSheet.NothingDetected -> NothingDetectedSheet(
            labels = labels,
            onExtractAnyway = { onIntent(BrowserIntent.ExtractWholePage) },
            onDismiss = { onIntent(BrowserIntent.DismissSheet) },
        )
    }
}
