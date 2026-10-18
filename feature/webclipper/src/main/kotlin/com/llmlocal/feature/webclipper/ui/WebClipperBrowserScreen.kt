package com.llmlocal.feature.webclipper.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
 * hidden while the IME is visible. Long-pressing the FAB offers "Hide on
 * this page"; while hidden for the current host a docked "Show clip button"
 * chip sits bottom-start instead. The WebView slot is a traversal group so
 * TalkBack reads the page as one region after the clip button.
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
    val hasPage = state.url.isNotBlank()
    val showFab = hasPage && !imeVisible && !state.fabHiddenForPage
    val showRestoreChip = hasPage && !imeVisible && state.fabHiddenForPage

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
                    onHideForPage = { onIntent(BrowserIntent.HideFabForPage) },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { isTraversalGroup = true },
            ) {
                webView(Modifier.fillMaxSize())
            }
            // Docked replacement for a hidden clip button (UX spec §2): a
            // 48 dp chip bottom-start, out of the way of site chrome that
            // usually sits bottom-end (cookie dialogs, sticky footers).
            if (showRestoreChip) {
                ElevatedAssistChip(
                    onClick = { onIntent(BrowserIntent.ShowFab) },
                    label = { Text(labels.showClipButton) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.ContentCut,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .height(48.dp),
                )
            }
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
