package com.llmlocal.recipekeeper.recipes.presentation.library

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.llmlocal.recipekeeper.recipes.presentation.RecipeKeeperLibraryLabels

/**
 * Shows "Recipe deleted / Undo" and routes the outcome: [onUndo] when the
 * action is tapped, [onDismissed] when the snackbar goes away without it
 * (timeout, swipe, or the dismiss icon).
 *
 * `Short` keeps the snackbar inside [PendingRecipeDeletes]'s 5 s window
 * on a normal device; accessibility services may extend it, which is why
 * a late undo reports `undoDeleteFailed` instead of silently failing.
 */
suspend fun SnackbarHostState.showRecipeDeleted(
    labels: RecipeKeeperLibraryLabels,
    onUndo: () -> Unit,
    onDismissed: () -> Unit,
) {
    val result = showSnackbar(
        message = labels.recipeDeleted,
        actionLabel = labels.undo,
        withDismissAction = true,
        duration = SnackbarDuration.Short,
    )
    when (result) {
        SnackbarResult.ActionPerformed -> onUndo()
        SnackbarResult.Dismissed -> onDismissed()
    }
}
