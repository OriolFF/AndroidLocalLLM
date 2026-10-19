package com.llmlocal.recipekeeper.recipes.presentation.library

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llmlocal.core.designsystem.component.AppScaffold
import com.llmlocal.recipekeeper.recipes.presentation.RecipeKeeperLibraryLabels
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Stateful wrapper around [LibraryScreen]: creates [LibraryViewModel] via
 * Koin, renders the `AppScaffold` top bar (title, Search toggle, Models
 * action) with the [LibraryFab] in the scaffold's FAB slot, and turns
 * [LibraryEffect]s into navigation callbacks, the share intent and the
 * undo-delete snackbar.
 *
 * Undo-delete goes through [showRecipeDeleted]: Undo → [LibraryIntent.UndoDelete],
 * dismissed early → [LibraryIntent.ConfirmDelete]; the ViewModel answers a
 * late undo with [LibraryEffect.UndoFailed].
 */
@Composable
fun LibraryRoute(
    labels: RecipeKeeperLibraryLabels,
    snackbarHostState: SnackbarHostState,
    onOpenRecipe: (id: String) -> Unit,
    onOpenBrowser: (url: String?) -> Unit,
    onOpenModelManagement: () -> Unit,
    onOpenEditor: (id: String?) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LibraryEffect.NavigateToRecipe -> onOpenRecipe(effect.id)
                is LibraryEffect.NavigateToBrowser -> onOpenBrowser(effect.url)
                LibraryEffect.NavigateToModelManagement -> onOpenModelManagement()
                is LibraryEffect.NavigateToEditor -> onOpenEditor(effect.id)
                is LibraryEffect.ShareRecipe -> context.shareText(
                    text = effect.text,
                    chooserTitle = labels.shareRecipe,
                )
                is LibraryEffect.RecipeDeleted -> launch {
                    snackbarHostState.showRecipeDeleted(
                        labels = labels,
                        onUndo = { viewModel.onIntent(LibraryIntent.UndoDelete(effect.id)) },
                        onDismissed = { viewModel.onIntent(LibraryIntent.ConfirmDelete(effect.id)) },
                    )
                }
                LibraryEffect.UndoFailed -> launch {
                    snackbarHostState.showSnackbar(labels.undoDeleteFailed)
                }
            }
        }
    }

    AppScaffold(
        title = labels.libraryTitle,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { LibraryFab(state = state, labels = labels, onIntent = viewModel::onIntent) },
        actions = {
            if (!state.searchActive) {
                IconButton(onClick = { viewModel.onIntent(LibraryIntent.OpenSearch) }) {
                    Icon(Icons.Outlined.Search, contentDescription = labels.searchActionDescription)
                }
            }
            IconButton(onClick = { viewModel.onIntent(LibraryIntent.CreateRecipe) }) {
                Icon(Icons.Outlined.Add, contentDescription = labels.newRecipe)
            }
            IconButton(onClick = { viewModel.onIntent(LibraryIntent.OpenModelManagement) }) {
                Icon(Icons.Outlined.Psychology, contentDescription = labels.manageModelsDescription)
            }
        },
    ) { padding ->
        LibraryScreen(
            state = state,
            labels = labels,
            onIntent = viewModel::onIntent,
            modifier = Modifier.padding(padding),
        )
    }
}

/** Fires a plain-text `ACTION_SEND` chooser. Shared by the library and detail routes. */
internal fun android.content.Context.shareText(text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, chooserTitle))
}
