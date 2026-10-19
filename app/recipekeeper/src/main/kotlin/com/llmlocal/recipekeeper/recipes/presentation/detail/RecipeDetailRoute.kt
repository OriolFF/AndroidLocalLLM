package com.llmlocal.recipekeeper.recipes.presentation.detail

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llmlocal.recipekeeper.recipes.presentation.RecipeKeeperLibraryLabels
import com.llmlocal.recipekeeper.recipes.presentation.library.shareText
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful wrapper around [RecipeDetailScreen]: creates
 * [RecipeDetailViewModel] via Koin with [recipeId] as a parameter and
 * turns effects into the share intent and navigation callbacks.
 *
 * @param onDeleted the host pops this screen and shows the undo snackbar
 */
@Composable
fun RecipeDetailRoute(
    recipeId: String,
    labels: RecipeKeeperLibraryLabels,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenBrowser: (url: String) -> Unit,
    onDeleted: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeDetailViewModel = koinViewModel { parametersOf(recipeId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RecipeDetailEffect.ShareRecipe -> context.shareText(
                    text = effect.text,
                    chooserTitle = labels.shareRecipe,
                )
                is RecipeDetailEffect.NavigateToBrowser -> onOpenBrowser(effect.url)
                is RecipeDetailEffect.Deleted -> onDeleted(effect.id)
                RecipeDetailEffect.PicturesRetryFailed -> launch { snackbarHostState.showSnackbar(labels.retryPicturesFailed) }
            }
        }
    }

    RecipeDetailScreen(
        state = state,
        labels = labels,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        modifier = modifier,
    )
}
