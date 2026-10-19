package com.llmlocal.recipekeeper.recipes.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.recipes.domain.usecase.AddRecipePictureUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.DiscardRecipeEditsUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.LoadRecipeForEditUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.SaveRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.SplitPastedLinesUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * MVI ViewModel of the recipe editor (`recipe/new`, `recipe/{id}/edit`).
 * F0 contract only: constructor and public surface are frozen, the body
 * arrives with stream F3.
 *
 * @param recipeId `null` creates a new recipe
 * @param savedStateHandle holds the draft across process death (F3 defines the key)
 */
class RecipeEditorViewModel(
    private val recipeId: String?,
    private val savedStateHandle: SavedStateHandle,
    private val loadForEdit: LoadRecipeForEditUseCase,
    private val saveRecipe: SaveRecipeUseCase,
    private val addPicture: AddRecipePictureUseCase,
    private val discardEdits: DiscardRecipeEditsUseCase,
    private val splitPasted: SplitPastedLinesUseCase,
    private val dispatchers: DispatcherProvider,
    private val maxPictures: Int = 8,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RecipeEditorState(
            mode = if (recipeId == null) EditorMode.CREATE else EditorMode.EDIT,
            maxPictures = maxPictures,
        ),
    )
    val state: StateFlow<RecipeEditorState> = _state.asStateFlow()

    private val _effects = Channel<RecipeEditorEffect>(Channel.BUFFERED)
    val effects: Flow<RecipeEditorEffect> = _effects.receiveAsFlow()

    /** Single dispatch point for every user action. */
    fun onIntent(intent: RecipeEditorIntent) {
        TODO("F3")
    }
}
