package com.llmlocal.feature.recipe

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.llmlocal.feature.recipe.mvi.RecipeEffect

/**
 * Stateful wrapper around [RecipeScreen]. Wires the [RecipeViewModel] into
 * the composable: collects state, listens for one-shot effects (snackbars,
 * scroll commands), and forwards intents to the ViewModel.
 */
@Composable
fun RecipeRoute(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: RecipeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RecipeEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                RecipeEffect.ScrollToOutput -> { /* future: scroll to output card */ }
            }
        }
    }

    RecipeScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}
