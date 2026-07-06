package com.llmlocal.feature.llmtask

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llmlocal.feature.llmtask.mvi.LlmTaskEffect
import com.llmlocal.feature.llmtask.mvi.LlmTaskLabels
import com.llmlocal.feature.llmtask.mvi.LlmTaskState
import com.llmlocal.core.domain.task.LlmTaskConfig
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful wrapper around [LlmTaskScreen]. Wires the [LlmTaskViewModel] into
 * the composable: collects state, listens for one-shot effects (snackbars,
 * scroll commands, navigation), and forwards intents to the ViewModel.
 *
 * The [config] and [labels] are passed via Koin's parameter mechanism so the
 * same module definition can serve any app that supplies its own.
 *
 * @param renderOutput rendering for the parsed result. The route passes
 *   the current [LlmTaskState] so the app can render streamed text +
 *   parsed result + generating indicator together.
 * @param onNavigateToModelManagement invoked when the VM emits
 *   [LlmTaskEffect.NavigateToModelManagement]. In a NavHost-backed host this
 *   would call `navController.navigate(...)`; in this template we use the
 *   callback so [LlmTaskRoute] stays host-agnostic.
 */
@Composable
fun LlmTaskRoute(
    config: LlmTaskConfig<*>,
    labels: LlmTaskLabels,
    renderOutput: @Composable (LlmTaskState) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateToModelManagement: () -> Unit = {},
    viewModel: LlmTaskViewModel = koinViewModel { parametersOf(config, labels) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LlmTaskEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                LlmTaskEffect.ScrollToOutput -> { /* future: scroll to output card */ }
                LlmTaskEffect.NavigateToModelManagement -> onNavigateToModelManagement()
            }
        }
    }

    LlmTaskScreen(
        state = state,
        onIntent = viewModel::onIntent,
        labels = labels,
        renderOutput = renderOutput,
        modifier = modifier,
    )
}