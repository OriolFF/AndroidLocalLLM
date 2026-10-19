package com.llmlocal.recipekeeper.recipes.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.recipes.domain.usecase.DeleteRecipeWithUndoUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ResetChecksUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.RetryRecipePicturesUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ShareRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ToggleFavouriteUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ToggleIngredientCheckedUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * MVI ViewModel for one recipe (`recipe/{recipeId}`).
 *
 * Observes [ObserveRecipeUseCase] so edits made elsewhere (another screen
 * ticking ingredients, a re-extraction replacing the row) show up live.
 * Ingredient checks go through [ToggleIngredientCheckedUseCase] /
 * [ResetChecksUseCase], which persist them; the state takes the returned
 * recipe and the repository's next emission confirms it.
 *
 * Delete hands the id to [DeleteRecipeWithUndoUseCase] (shared undo window)
 * and emits [RecipeDetailEffect.Deleted]; the host pops the screen and shows
 * the undo snackbar from its own scope so it survives the pop.
 *
 * "Retry pictures" (after an offline clip) is [RetryRecipePicturesUseCase];
 * the repository's next emission shows the hero.
 */
class RecipeDetailViewModel(
    private val recipeId: String,
    observeRecipe: ObserveRecipeUseCase,
    private val toggleIngredient: ToggleIngredientCheckedUseCase,
    private val resetChecks: ResetChecksUseCase,
    private val deleteWithUndo: DeleteRecipeWithUndoUseCase,
    private val retryPictures: RetryRecipePicturesUseCase,
    private val shareRecipe: ShareRecipeUseCase,
    private val toggleFavourite: ToggleFavouriteUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeDetailState())
    val state: StateFlow<RecipeDetailState> = _state.asStateFlow()

    private val _effects = Channel<RecipeDetailEffect>(Channel.BUFFERED)
    val effects: Flow<RecipeDetailEffect> = _effects.receiveAsFlow()

    init {
        observeRecipe(recipeId)
            .flowOn(dispatchers.io)
            .onEach { recipe -> _state.update { it.copy(recipe = recipe, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    /** Single dispatch point for every user action. */
    fun onIntent(intent: RecipeDetailIntent) {
        when (intent) {
            is RecipeDetailIntent.ToggleIngredient -> onToggleIngredient(intent.index)
            RecipeDetailIntent.ResetChecks -> onResetChecks()
            is RecipeDetailIntent.SelectStep -> _state.update {
                it.copy(currentStep = if (it.currentStep == intent.index) null else intent.index)
            }
            RecipeDetailIntent.ToggleKeepScreenOn -> _state.update { it.copy(keepScreenOn = !it.keepScreenOn) }
            RecipeDetailIntent.Share -> viewModelScope.launch {
                val text = withContextIo { shareRecipe(recipeId) } ?: return@launch
                _effects.send(RecipeDetailEffect.ShareRecipe(text))
            }
            RecipeDetailIntent.RetryPictures -> onRetryPictures()
            RecipeDetailIntent.OpenOriginal, RecipeDetailIntent.ReExtract -> _state.value.recipe?.let { recipe ->
                emit(RecipeDetailEffect.NavigateToBrowser(recipe.sourceUrl))
            }
            RecipeDetailIntent.Edit -> emit(RecipeDetailEffect.NavigateToEditor(recipeId))
            RecipeDetailIntent.ToggleFavourite -> _state.value.recipe?.let { recipe ->
                viewModelScope.launch { toggleFavourite(recipeId, !recipe.isFavourite) }
            }
            RecipeDetailIntent.RequestDelete -> _state.update { it.copy(showDeleteDialog = true) }
            RecipeDetailIntent.DismissDelete -> _state.update { it.copy(showDeleteDialog = false) }
            RecipeDetailIntent.ConfirmDelete -> {
                _state.update { it.copy(showDeleteDialog = false) }
                deleteWithUndo.schedule(recipeId)
                emit(RecipeDetailEffect.Deleted(recipeId))
            }
        }
    }

    // Serialised so two quick taps never toggle from the same stale recipe.
    private val checksMutex = Mutex()

    private fun onToggleIngredient(index: Int) {
        viewModelScope.launch {
            checksMutex.withLock {
                val recipe = _state.value.recipe ?: return@launch
                val updated = withContextIo { toggleIngredient(recipe, index) } ?: return@launch
                _state.update { it.copy(recipe = updated) }
            }
        }
    }

    private fun onResetChecks() {
        viewModelScope.launch {
            checksMutex.withLock {
                val recipe = _state.value.recipe ?: return@launch
                val updated = withContextIo { resetChecks(recipe) }
                _state.update { it.copy(recipe = updated) }
            }
        }
    }

    private fun onRetryPictures() {
        val recipe = _state.value.recipe ?: return
        if (_state.value.retryingPictures) return
        _state.update { it.copy(retryingPictures = true) }
        viewModelScope.launch {
            try {
                if (!retryPictures(recipe)) _effects.send(RecipeDetailEffect.PicturesRetryFailed)
            } finally {
                _state.update { it.copy(retryingPictures = false) }
            }
        }
    }

    private suspend fun <T> withContextIo(block: suspend () -> T): T =
        withContext(dispatchers.io) { block() }

    private fun emit(effect: RecipeDetailEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
