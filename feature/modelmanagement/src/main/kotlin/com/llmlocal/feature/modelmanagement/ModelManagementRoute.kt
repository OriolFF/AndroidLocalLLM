package com.llmlocal.feature.modelmanagement

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llmlocal.feature.modelmanagement.mvi.LocalModelManagementLabels
import com.llmlocal.feature.modelmanagement.mvi.ModelManagementEffect
import com.llmlocal.feature.modelmanagement.mvi.ModelManagementLabels
import org.koin.androidx.compose.koinViewModel

/**
 * Stateful wrapper around [ModelManagementScreen]. Wires the
 * [ModelManagementViewModel] into the composable: collects state, listens
 * for one-shot effects (snackbars), and forwards intents to the
 * ViewModel.
 *
 * Provides [labels] to the screen and its components via
 * [LocalModelManagementLabels].
 */
@Composable
fun ModelManagementRoute(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    labels: ModelManagementLabels,
    viewModel: ModelManagementViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ModelManagementEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    CompositionLocalProvider(LocalModelManagementLabels provides labels) {
        ModelManagementScreen(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = modifier,
        )
    }
}