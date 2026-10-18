package com.llmlocal.recipekeeper.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.domain.RecipeRepository
import com.llmlocal.recipekeeper.library.PendingRecipeDeletes
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
import kotlinx.coroutines.withContext

/**
 * MVI ViewModel for one recipe (`recipe/{recipeId}`).
 *
 * Observes [RecipeRepository.observe] so edits made elsewhere (another
 * screen ticking ingredients, a re-extraction replacing the row) show up
 * live. Ingredient checks are applied optimistically to the state and
 * persisted with [RecipeRepository.setChecked]; the repository's next
 * emission confirms them.
 *
 * Delete hands the id to [PendingRecipeDeletes] (shared undo window) and
 * emits [RecipeDetailEffect.Deleted]; the host pops the screen and shows
 * the undo snackbar from its own scope so it survives the pop.
 */
class RecipeDetailViewModel(
    private val recipeId: String,
    private val repository: RecipeRepository,
    private val pendingDeletes: PendingRecipeDeletes,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeDetailState())
    val state: StateFlow<RecipeDetailState> = _state.asStateFlow()

    private val _effects = Channel<RecipeDetailEffect>(Channel.BUFFERED)
    val effects: Flow<RecipeDetailEffect> = _effects.receiveAsFlow()

    init {
        repository.observe(recipeId)
            .flowOn(dispatchers.io)
            .onEach { recipe -> _state.update { it.copy(recipe = recipe, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    /** Single dispatch point for every user action. */
    fun onIntent(intent: RecipeDetailIntent) {
        when (intent) {
            is RecipeDetailIntent.ToggleIngredient -> toggleIngredient(intent.index)
            RecipeDetailIntent.ResetChecks -> setChecked(emptySet())
            is RecipeDetailIntent.SelectStep -> _state.update {
                it.copy(currentStep = if (it.currentStep == intent.index) null else intent.index)
            }
            RecipeDetailIntent.ToggleKeepScreenOn -> _state.update { it.copy(keepScreenOn = !it.keepScreenOn) }
            RecipeDetailIntent.Share -> _state.value.recipe?.let { recipe ->
                emit(RecipeDetailEffect.ShareRecipe(recipe.title, recipe.sourceUrl))
            }
            RecipeDetailIntent.OpenOriginal, RecipeDetailIntent.ReExtract -> _state.value.recipe?.let { recipe ->
                emit(RecipeDetailEffect.NavigateToBrowser(recipe.sourceUrl))
            }
            RecipeDetailIntent.RequestDelete -> _state.update { it.copy(showDeleteDialog = true) }
            RecipeDetailIntent.DismissDelete -> _state.update { it.copy(showDeleteDialog = false) }
            RecipeDetailIntent.ConfirmDelete -> {
                _state.update { it.copy(showDeleteDialog = false) }
                pendingDeletes.schedule(recipeId)
                emit(RecipeDetailEffect.Deleted(recipeId))
            }
        }
    }

    private fun toggleIngredient(index: Int) {
        val recipe = _state.value.recipe ?: return
        if (index !in recipe.ingredients.indices) return
        val checked = recipe.checkedIngredients.toMutableSet().apply {
            if (!add(index)) remove(index)
        }
        setChecked(checked)
    }

    private fun setChecked(checked: Set<Int>) {
        val recipe = _state.value.recipe ?: return
        _state.update { it.copy(recipe = recipe.copy(checkedIngredients = checked)) }
        viewModelScope.launch {
            withContext(dispatchers.io) { repository.setChecked(recipeId, checked) }
        }
    }

    private fun emit(effect: RecipeDetailEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
