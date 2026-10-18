package com.llmlocal.feature.webclipper

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Entry point for the extraction screen: phase progress, the app's draft
 * slot, picture strip and Stop button. Reads the candidate from
 * [ClipSession] by [candidateId].
 *
 * Creates `ExtractionViewModel` via `koinViewModel { parametersOf(candidateId, config, sink, labels) }`.
 * Implementation lands in WS4; this stub only fixes the signature.
 *
 * @param renderDraft the app renders its own skeleton-that-fills-in from the
 *   raw streamed text (mirrors `LlmTaskRoute.renderOutput`)
 * @param onSaved called with the saved item id
 * @param onCancelled called when the user stops or goes back to the page
 */
@Composable
fun <T : Any> WebClipperExtractionRoute(
    candidateId: String,
    config: WebClipConfig<T>,
    sink: ClipSink<T>,
    labels: WebClipperLabels,
    renderDraft: @Composable (streamedText: String, isRunning: Boolean) -> Unit,
    onSaved: (id: String) -> Unit,
    onCancelled: () -> Unit,
    onNavigateToModelManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(text = "WS4", modifier = modifier)
}
