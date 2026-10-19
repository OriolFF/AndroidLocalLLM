package com.llmlocal.feature.modelmanagement.presentation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llmlocal.feature.modelmanagement.presentation.mvi.LocalModelManagementLabels
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementEffect
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementLabels
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful wrapper around [ModelManagementScreen]. Wires the
 * [ModelManagementViewModel] into the composable: collects state, listens
 * for one-shot effects (snackbars), and forwards intents to the
 * ViewModel.
 *
 * [labels] is passed twice — once into the ViewModel via Koin's
 * parameter mechanism so its snackbars are brand-appropriate, and once
 * into the composable tree via [LocalModelManagementLabels] so the
 * shared screen and components can read labels without plumbing them
 * through every call site.
 */
@Composable
fun ModelManagementRoute(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    labels: ModelManagementLabels,
    viewModel: ModelManagementViewModel = koinViewModel { parametersOf(labels) },
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
