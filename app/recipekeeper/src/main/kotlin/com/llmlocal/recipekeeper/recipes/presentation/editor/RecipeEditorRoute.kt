package com.llmlocal.recipekeeper.recipes.presentation.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful wrapper of the recipe editor. Owns the photo pickers, the back
 * handler and the effect plumbing: `Saved` / `Closed` become navigation
 * callbacks, `Notify` becomes a snackbar through [labels], and focus / scroll
 * commands are forwarded to [RecipeEditorScreen] (which owns the list states).
 *
 * @param recipeId `null` creates a new recipe
 * @param onSaved navigation after a save; [EditorMode.CREATE] opens the detail, [EditorMode.EDIT] pops
 * @param onClosed the editor was closed (or discard confirmed): pop it
 */
@Composable
fun RecipeEditorRoute(
    recipeId: String?,
    labels: RecipeEditorLabels,
    snackbarHostState: SnackbarHostState,
    onSaved: (id: String, mode: EditorMode) -> Unit,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeEditorViewModel = koinViewModel { parametersOf(recipeId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentLabels by rememberUpdatedState(labels)
    val currentOnSaved by rememberUpdatedState(onSaved)
    val currentOnClosed by rememberUpdatedState(onClosed)
    val uiEffects = remember { Channel<RecipeEditorEffect>(Channel.BUFFERED) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RecipeEditorEffect.Saved -> currentOnSaved(effect.id, effect.mode)
                RecipeEditorEffect.Closed -> currentOnClosed()
                is RecipeEditorEffect.Notify -> launch {
                    snackbarHostState.showSnackbar(
                        when (effect.kind) {
                            EditorNotice.SAVE_FAILED -> currentLabels.saveFailed
                            EditorNotice.PICTURE_FAILED -> currentLabels.pictureFailed
                            EditorNotice.PICTURES_LIMIT -> currentLabels.picturesLimit(viewModel.state.value.maxPictures)
                        },
                    )
                }
                is RecipeEditorEffect.FocusLine,
                RecipeEditorEffect.FocusTitle,
                is RecipeEditorEffect.ScrollTo,
                -> uiEffects.send(effect)
            }
        }
    }

    BackHandler(enabled = state.isDirty) { viewModel.onIntent(RecipeEditorIntent.RequestClose) }

    val slots = (state.maxPictures - (state.edit?.pictures?.size ?: 0) - state.importingPictures).coerceAtLeast(0)
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onIntent(RecipeEditorIntent.PicturesPicked(listOf(uri.toString())))
    }
    // The multi-pick contract requires maxItems > 1, so it is only used while two or more slots are free.
    val pickMany = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = slots.coerceAtLeast(2)),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.onIntent(RecipeEditorIntent.PicturesPicked(uris.map { it.toString() }))
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    RecipeEditorScreen(
        state = state,
        labels = labels,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
        onAddPictures = { if (slots >= 2) pickMany.launch(imageOnly) else pickOne.launch(imageOnly) },
        uiEffects = remember(uiEffects) { uiEffects.receiveAsFlow() },
        modifier = modifier,
    )
}
