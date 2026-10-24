package com.llmlocal.recipekeeper.recipes.presentation.library

import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatus
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.AppLoadingIndicator
import com.llmlocal.core.designsystem.component.EmptyState
import com.llmlocal.core.designsystem.component.GradientHero
import com.llmlocal.feature.modelmanagement.presentation.components.DownloadProgressBar
import com.llmlocal.recipekeeper.recipes.presentation.RecipeKeeperLibraryLabels

/**
 * Stateless library screen (UX spec §5). Reads [state], dispatches
 * [LibraryIntent]; the surrounding `AppScaffold` (top bar with Search and
 * Models actions, and the [LibraryFab] in its FAB slot) is owned by
 * `LibraryRoute`.
 *
 * Top to bottom:
 *  1. search field — pinned above the grid while [LibraryState.searchActive];
 *  2. no-model hero (`GradientHero`, spans every column) or the compact
 *     download card while a download is RUNNING; both open model management;
 *  3. adaptive grid of [RecipeTile]s (168dp min cell, 16dp gutters, 12dp gaps).
 *
 * Loading shows [AppLoadingIndicator] only until the first repository
 * emission; afterwards the grid updates in place.
 */
@Composable
fun LibraryScreen(
    state: LibraryState,
    labels: RecipeKeeperLibraryLabels,
    onIntent: (LibraryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(visible = state.searchActive) {
            LibrarySearchField(
                query = state.query,
                labels = labels,
                onQueryChange = { onIntent(LibraryIntent.SearchChanged(it)) },
                onClear = { onIntent(LibraryIntent.ClearSearch) },
                onClose = { onIntent(LibraryIntent.CloseSearch) },
            )
        }
        if (!state.isLoading && !state.isEmptyLibrary) {
            LibraryFilterRow(
                filter = state.filter,
                labels = labels,
                onFavouritesChange = { onIntent(LibraryIntent.SetFavouritesFilter(it)) },
            )
        }
        if (state.isLoading) {
            AppLoadingIndicator(modifier = Modifier.fillMaxSize())
        } else {
            LibraryGrid(
                state = state,
                labels = labels,
                onIntent = onIntent,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * "Open browser" FAB → browser, for the `AppScaffold.floatingActionButton`
 * slot (so the undo-delete snackbar is placed above it, not over it —
 * WS10 bug 11). Hidden on the onboarding empty state, whose own button is
 * already the single primary action, and while loading.
 */
@Composable
fun LibraryFab(
    state: LibraryState,
    labels: RecipeKeeperLibraryLabels,
    onIntent: (LibraryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showFab = !state.isLoading && !state.isEmptyLibrary
    AnimatedVisibility(
        visible = showFab,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier.imePadding(),
    ) {
        ExtendedFloatingActionButton(
            onClick = { onIntent(LibraryIntent.ClipRecipe) },
            icon = { Icon(Icons.Outlined.ContentCut, contentDescription = null) },
            text = { Text(text = labels.libraryFabClip) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            // Explicit name: the extended-FAB label did not surface in the
            // device-QA hierarchy dump (node came back NAF with no text).
            modifier = Modifier.semantics { contentDescription = labels.libraryFabClip },
        )
    }
}

@Composable
private fun LibraryGrid(
    state: LibraryState,
    labels: RecipeKeeperLibraryLabels,
    onIntent: (LibraryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        // Bottom padding leaves room for the FAB + gesture bar so the last
        // row is never hidden under them.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        when (val status = state.modelStatus) {
            LibraryModelStatus.NoModel -> item(span = { GridItemSpan(maxLineSpan) }, key = "no_model") {
                GradientHero(
                    title = labels.libraryNoModelTitle,
                    subtitle = labels.libraryNoModelSubtitle,
                    icon = Icons.Outlined.Memory,
                    actionLabel = labels.chooseModelAction,
                    onAction = { onIntent(LibraryIntent.OpenModelManagement) },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            is LibraryModelStatus.Downloading -> item(span = { GridItemSpan(maxLineSpan) }, key = "downloading") {
                DownloadCard(
                    status = status,
                    labels = labels,
                    onClick = { onIntent(LibraryIntent.OpenModelManagement) },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            is LibraryModelStatus.Ready -> Unit
        }

        when {
            state.isEmptyLibrary -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = labels.libraryEmptyTitle,
                    message = labels.libraryEmptyMessage,
                    actionLabel = labels.openBrowser,
                    onAction = { onIntent(LibraryIntent.ClipRecipe) },
                )
                TextButton(
                    onClick = { onIntent(LibraryIntent.OpenSourceChooser) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(labels.moreWaysToAdd) }
            }
            state.isFavouritesEmpty -> item(span = { GridItemSpan(maxLineSpan) }, key = "favourites_empty") {
                EmptyState(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = labels.favouritesEmptyTitle,
                    message = labels.favouritesEmptyMessage,
                    actionLabel = labels.showAllRecipes,
                    onAction = { onIntent(LibraryIntent.SetFavouritesFilter(false)) },
                )
            }
            state.isSearchEmpty -> item(span = { GridItemSpan(maxLineSpan) }, key = "search_empty") {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = if (state.filter.favouritesOnly) {
                        labels.searchEmptyFavouritesTitle(state.query.trim())
                    } else {
                        labels.searchEmptyTitle(state.query.trim())
                    },
                    actionLabel = labels.clearSearch,
                    onAction = { onIntent(LibraryIntent.ClearSearch) },
                )
            }
            else -> items(state.recipes, key = { it.id }) { summary ->
                RecipeTile(
                    summary = summary,
                    labels = labels,
                    onClick = { onIntent(LibraryIntent.OpenRecipe(summary.id)) },
                    onDelete = { onIntent(LibraryIntent.DeleteRecipe(summary.id)) },
                    onShare = { onIntent(LibraryIntent.ShareRecipe(summary.id)) },
                    onOpenOriginal = { onIntent(LibraryIntent.OpenOriginal(summary.id)) },
                    onToggleFavourite = { onIntent(LibraryIntent.ToggleFavourite(summary.id, it)) },
                    onEdit = { onIntent(LibraryIntent.EditRecipe(summary.id)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * Search field pinned above the grid. Requests focus (and the keyboard)
 * when it appears; the trailing button clears the text while there is
 * any, and closes the search otherwise.
 */
@Composable
private fun LibrarySearchField(
    query: String,
    labels: RecipeKeeperLibraryLabels,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .focusRequester(focusRequester),
        placeholder = { Text(labels.searchHint) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = labels.clearSearch)
                }
            } else {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = labels.closeSearch)
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
    )
}

/**
 * Compact "Downloading <model>…" card shown in place of the no-model hero
 * while the selected model's download is RUNNING. The whole card opens
 * model management, where the download can be watched or cancelled.
 */
@Composable
private fun DownloadCard(
    status: LibraryModelStatus.Downloading,
    labels: RecipeKeeperLibraryLabels,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = labels.downloadingModel(status.modelName)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = labels.tapToManageModels, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = labels.downloadingModelDescription(title, status.percent) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            DownloadProgressBar(progress = status.progress)
        }
    }
}
