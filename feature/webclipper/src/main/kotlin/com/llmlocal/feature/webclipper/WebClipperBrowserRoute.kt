package com.llmlocal.feature.webclipper

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Entry point for the in-app browser: URL bar, WebView, clip button and the
 * candidate / nothing-detected sheets. Owns its own `Scaffold`.
 *
 * Creates `BrowserViewModel` via `koinViewModel { parametersOf(config, labels) }`
 * and forwards its effects to the navigation callbacks. Implementation lands
 * in WS4; this stub only fixes the signature.
 *
 * @param initialUrl page to open on first composition, or `null` for the blank start
 * @param onNavigateToExtraction called with the picked candidate id
 * @param onBackToLibrary leading back arrow (WebView history is popped first)
 * @param snackbarHostState the host's single snackbar state
 */
@Composable
fun <T : Any> WebClipperBrowserRoute(
    config: WebClipConfig<T>,
    sink: ClipSink<T>,
    labels: WebClipperLabels,
    initialUrl: String?,
    onNavigateToModelManagement: () -> Unit,
    onNavigateToExtraction: (candidateId: String) -> Unit,
    onBackToLibrary: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Text(text = "WS4", modifier = modifier)
}
