package com.llmlocal.recipekeeper.recipes.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.recipes.domain.usecase.DeleteRecipeWithUndoUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveLibraryModelStatusUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveLibraryUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ShareRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ToggleFavouriteUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MVI ViewModel for the library screen.
 *
 * [LibraryState.recipes] is derived reactively from the search
 * [LibraryState.query] and [LibraryState.filter] through [ObserveLibraryUseCase] (`flatMapLatest`, so
 * a stale search result never lands after the query changed); that use case
 * already hides recipes inside the delete-undo window. The model status
 * from [ObserveLibraryModelStatusUseCase] drives the no-model hero and the
 * download card.
 *
 * Share / open-original need the recipe's source URL, which the grid
 * projection does not carry, so those intents resolve it through
 * [ShareRecipeUseCase] / [ObserveRecipeUseCase] and answer with an effect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val observeLibrary: ObserveLibraryUseCase,
    observeModelStatus: ObserveLibraryModelStatusUseCase,
    private val observeRecipe: ObserveRecipeUseCase,
    private val deleteWithUndo: DeleteRecipeWithUndoUseCase,
    private val shareRecipe: ShareRecipeUseCase,
    private val toggleFavourite: ToggleFavouriteUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val modelStatus = observeModelStatus()

    private val _state = MutableStateFlow(LibraryState(modelStatus = modelStatus.value))
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _effects = Channel<LibraryEffect>(Channel.BUFFERED)
    val effects: Flow<LibraryEffect> = _effects.receiveAsFlow()

    init {
        observeRecipes()
        modelStatus
            .onEach { status -> _state.update { it.copy(modelStatus = status) } }
            .launchIn(viewModelScope)
    }

    /** Single dispatch point for every user action. */
    fun onIntent(intent: LibraryIntent) {
        when (intent) {
            LibraryIntent.OpenSearch -> _state.update { it.copy(searchActive = true) }
            LibraryIntent.CloseSearch -> _state.update { it.copy(searchActive = false, query = "") }
            is LibraryIntent.SearchChanged -> _state.update { it.copy(query = intent.value) }
            LibraryIntent.ClearSearch -> _state.update { it.copy(query = "") }

            is LibraryIntent.OpenRecipe -> emit(LibraryEffect.NavigateToRecipe(intent.id))
            LibraryIntent.ClipRecipe -> emit(LibraryEffect.NavigateToBrowser(url = null))
            LibraryIntent.OpenModelManagement -> emit(LibraryEffect.NavigateToModelManagement)

            is LibraryIntent.ShareRecipe -> viewModelScope.launch {
                val text = withContext(dispatchers.io) { shareRecipe(intent.id) } ?: return@launch
                emit(LibraryEffect.ShareRecipe(text))
            }
            is LibraryIntent.OpenOriginal -> viewModelScope.launch {
                val recipe = withContext(dispatchers.io) { observeRecipe(intent.id).first() } ?: return@launch
                emit(LibraryEffect.NavigateToBrowser(recipe.sourceUrl))
            }

            is LibraryIntent.DeleteRecipe -> {
                deleteWithUndo.schedule(intent.id)
                emit(LibraryEffect.RecipeDeleted(intent.id))
            }
            is LibraryIntent.UndoDelete -> {
                if (!deleteWithUndo.undo(intent.id)) emit(LibraryEffect.UndoFailed)
            }
            is LibraryIntent.ConfirmDelete -> viewModelScope.launch { deleteWithUndo.commit(intent.id) }

            is LibraryIntent.SetFavouritesFilter ->
                _state.update { it.copy(filter = it.filter.copy(favouritesOnly = intent.on)) }
            is LibraryIntent.ToggleFavourite ->
                viewModelScope.launch { toggleFavourite(intent.id, intent.favourite) }
            is LibraryIntent.EditRecipe -> emit(LibraryEffect.NavigateToEditor(intent.id))
            LibraryIntent.CreateRecipe -> emit(LibraryEffect.NavigateToEditor(null))
        }
    }

    private fun observeRecipes() {
        _state
            .map { it.query.trim() to it.filter }
            .distinctUntilChanged()
            .flatMapLatest { (query, filter) -> observeLibrary(query, filter) }
            .flowOn(dispatchers.default)
            .onEach { recipes -> _state.update { it.copy(recipes = recipes, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    private fun emit(effect: LibraryEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
