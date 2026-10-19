package com.llmlocal.recipekeeper.recipes.presentation.editor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.llmlocal.core.designsystem.component.AppLoadingIndicator
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful wrapper of the recipe editor. F0 placeholder: renders a loading
 * indicator; stream F3 replaces the body with the real screen. The
 * signature is frozen so the nav host compiles against it.
 *
 * @param recipeId `null` creates a new recipe
 * @param onSaved navigation after a save; [EditorMode.CREATE] opens the detail, [EditorMode.EDIT] pops
 * @param onClosed the editor was closed (or discard confirmed): pop it
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun RecipeEditorRoute(
    recipeId: String?,
    labels: RecipeEditorLabels,
    snackbarHostState: SnackbarHostState,
    onSaved: (id: String, mode: EditorMode) -> Unit,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeEditorViewModel = koinViewModel { parametersOf(recipeId) },
) {
    AppLoadingIndicator(modifier = modifier)
}
