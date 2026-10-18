package com.llmlocal.recipekeeper.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.domain.ClippedRecipe
import com.llmlocal.recipekeeper.domain.RecipeRepository
import com.llmlocal.recipekeeper.domain.RecipeSummary
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
 * [LibraryState.recipes] is derived reactively from three sources:
 *
 *  1. the search [LibraryState.query] — blank → [RecipeRepository.observeAll],
 *     otherwise [RecipeRepository.search] (`flatMapLatest`, so a stale
 *     search result never lands after the query changed);
 *  2. [PendingRecipeDeletes.pending] — recipes inside the undo window are
 *     filtered out so they vanish immediately on every screen;
 *  3. [ModelStatusSource.status] — drives the no-model hero and the
 *     download card.
 *
 * Share / open-original need the source URL, which [RecipeSummary] does
 * not carry (it is a cheap grid projection), so those intents read the
 * full recipe once and answer with an effect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: RecipeRepository,
    private val modelStatusSource: ModelStatusSource,
    private val pendingDeletes: PendingRecipeDeletes,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState(modelStatus = modelStatusSource.status.value))
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _effects = Channel<LibraryEffect>(Channel.BUFFERED)
    val effects: Flow<LibraryEffect> = _effects.receiveAsFlow()

    init {
        observeRecipes()
        modelStatusSource.status
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

            is LibraryIntent.ShareRecipe -> withRecipe(intent.id) { recipe ->
                emit(LibraryEffect.ShareRecipe(recipe.title, recipe.sourceUrl))
            }
            is LibraryIntent.OpenOriginal -> withRecipe(intent.id) { recipe ->
                emit(LibraryEffect.NavigateToBrowser(recipe.sourceUrl))
            }

            is LibraryIntent.DeleteRecipe -> {
                pendingDeletes.schedule(intent.id)
                emit(LibraryEffect.RecipeDeleted(intent.id))
            }
            is LibraryIntent.UndoDelete -> {
                if (!pendingDeletes.undo(intent.id)) emit(LibraryEffect.UndoFailed)
            }
            is LibraryIntent.ConfirmDelete -> viewModelScope.launch { pendingDeletes.commit(intent.id) }
        }
    }

    private fun observeRecipes() {
        val source: Flow<List<RecipeSummary>> = _state
            .map { it.query.trim() }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (query.isBlank()) repository.observeAll() else repository.search(query)
            }
        combine(source, pendingDeletes.pending) { recipes, pending ->
            if (pending.isEmpty()) recipes else recipes.filterNot { it.id in pending }
        }
            .flowOn(dispatchers.default)
            .onEach { recipes -> _state.update { it.copy(recipes = recipes, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    private fun withRecipe(
        id: String,
        block: (ClippedRecipe) -> Unit,
    ) {
        viewModelScope.launch {
            val recipe = withContext(dispatchers.io) { repository.observe(id).first() } ?: return@launch
            block(recipe)
        }
    }

    private fun emit(effect: LibraryEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
