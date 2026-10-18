package com.llmlocal.feature.webclipper

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llmlocal.feature.webclipper.mvi.ExtractionEffect
import com.llmlocal.feature.webclipper.mvi.ExtractionIntent
import com.llmlocal.feature.webclipper.ui.WebClipperExtractionScreen
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Entry point for the extraction screen: phase progress, the app's draft
 * slot, picture strip and Stop button. Reads the candidate from
 * [ClipSession] by [candidateId]; if the session is empty (process death)
 * the ViewModel pops straight back.
 *
 * Creates [ExtractionViewModel] via `koinViewModel { parametersOf(candidateId, config, sink, labels) }`.
 * System back cancels, like the Close icon (no confirmation, per UX spec §4).
 *
 * @param renderDraft the app renders its own skeleton-that-fills-in from the
 *   raw streamed text (mirrors `LlmTaskRoute.renderOutput`)
 * @param onSaved called with the saved item id
 * @param onCancelled called when the user stops or goes back to the page
 * @param snackbarHostState optional host for "Clip cancelled" / "Saved";
 *   `null` drops those messages
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
    snackbarHostState: SnackbarHostState? = null,
) {
    val viewModel: ExtractionViewModel = koinViewModel(key = candidateId) {
        parametersOf(candidateId, config, sink, labels)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.isRunning) {
        if (!state.isRunning) return@LaunchedEffect
        elapsedSeconds = 0
        while (true) {
            delay(1_000)
            elapsedSeconds += 1
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExtractionEffect.NavigateToSaved -> onSaved(effect.id)
                ExtractionEffect.PopToBrowser -> onCancelled()
                is ExtractionEffect.ShowSnackbar -> snackbarHostState?.showSnackbar(effect.message)
            }
        }
    }

    BackHandler { viewModel.onIntent(ExtractionIntent.Cancel) }

    WebClipperExtractionScreen(
        state = state,
        labels = labels,
        elapsedSeconds = elapsedSeconds,
        renderDraft = renderDraft,
        onIntent = viewModel::onIntent,
        onNavigateToModelManagement = onNavigateToModelManagement,
        onViewSaved = { state.savedId?.let(onSaved) },
        modifier = modifier,
    )
}
