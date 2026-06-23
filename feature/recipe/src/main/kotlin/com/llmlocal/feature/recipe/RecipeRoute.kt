package com.llmlocal.feature.recipe

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llmlocal.feature.recipe.mvi.RecipeEffect
import org.koin.androidx.compose.koinViewModel

/**
 * Stateful wrapper around [RecipeScreen]. Wires the [RecipeViewModel] into
 * the composable: collects state, listens for one-shot effects (snackbars,
 * scroll commands, navigation), and forwards intents to the ViewModel.
 *
 * @param onNavigateToModelManagement invoked when the VM emits
 *   [RecipeEffect.NavigateToModelManagement]. In a NavHost-backed host this
 *   would call `navController.navigate(...)`; in this template we use the
 *   `onNavigateToModelManagement` callback so [RecipeRoute] stays
 *   host-agnostic.
 */
@Composable
fun RecipeRoute(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateToModelManagement: () -> Unit = {},
    viewModel: RecipeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RecipeEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                RecipeEffect.ScrollToOutput -> { /* future: scroll to output card */ }
                RecipeEffect.NavigateToModelManagement -> onNavigateToModelManagement()
            }
        }
    }

    RecipeScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}