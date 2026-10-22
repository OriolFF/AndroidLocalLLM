package com.llmlocal.recipekeeper.settings.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llmlocal.core.designsystem.component.AppScaffold
import com.llmlocal.recipekeeper.settings.presentation.mvi.SettingsEffect
import org.koin.androidx.compose.koinViewModel

/**
 * Stateful wrapper around [SettingsScreen]: creates [SettingsViewModel] via
 * Koin, owns the `AppScaffold` (title + back arrow) and turns
 * [SettingsEffect]s into navigation callbacks.
 */
@Composable
fun SettingsRoute(
    labels: SettingsLabels,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenModelManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.NavigateToModelManagement -> onOpenModelManagement()
            }
        }
    }

    AppScaffold(
        title = labels.title,
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = labels.back)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        SettingsScreen(
            state = state,
            labels = labels,
            onIntent = viewModel::onIntent,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
