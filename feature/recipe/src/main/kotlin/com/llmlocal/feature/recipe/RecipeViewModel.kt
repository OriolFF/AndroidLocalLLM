package com.llmlocal.feature.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.domain.model.RecipeEvent
import com.llmlocal.core.domain.usecase.GenerateRecipeUseCase
import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.llm.download.ModelDownloadProgressStore
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.core.llm.selection.LlmModelSelectionStore
import com.llmlocal.core.model.Ingredient
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.feature.recipe.mvi.ModelStatus
import com.llmlocal.feature.recipe.mvi.RecipeEffect
import com.llmlocal.feature.recipe.mvi.RecipeIntent
import com.llmlocal.feature.recipe.mvi.RecipeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Recipe-generation MVI ViewModel.
 *
 * Owns the [RecipeState] and processes [RecipeIntent]s dispatched by the
 * composable. The MVI loop is intentionally simple:
 *
 *  intent -> handler -> state update (+ optional effect)
 *
 * Heavy work (LLM generation) is launched on [viewModelScope] and runs on
 * the [DispatcherProvider.io] dispatcher. Model download / remove / select
 * live in the model-management feature; this ViewModel just observes the
 * selection store + installed set and routes the user there when nothing
 * is available.
 */
class RecipeViewModel(
    private val generateRecipe: GenerateRecipeUseCase,
    private val modelManager: LlmModelManager,
    private val selectionStore: LlmModelSelectionStore,
    private val progressStore: ModelDownloadProgressStore,
    private val realEngine: LlmEngine,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeState())
    val state: StateFlow<RecipeState> = _state.asStateFlow()

    private val _effects = Channel<RecipeEffect>(Channel.BUFFERED)
    val effects: Flow<RecipeEffect> = _effects.receiveAsFlow()

    private var generationJob: Job? = null

    init {
        observeSelection()
        onIntent(RecipeIntent.CheckModel)
    }

    /**
     * Single dispatch point for all user actions. The pattern lets the UI
     * stay declarative — `viewModel.onIntent(RecipeIntent.GenerateRecipe)`.
     */
    fun onIntent(intent: RecipeIntent) {
        when (intent) {
            is RecipeIntent.UpdateInput -> _state.update { it.copy(input = intent.text) }
            RecipeIntent.AddIngredient -> addCurrentInput()
            is RecipeIntent.RemoveIngredient -> removeIngredient(intent.name)
            RecipeIntent.ClearIngredients -> _state.update {
                it.copy(ingredients = emptyList(), recipe = null, streamedText = "")
            }
            RecipeIntent.GenerateRecipe -> startGeneration()
            RecipeIntent.CancelGeneration -> cancelGeneration()
            RecipeIntent.DismissError -> _state.update { it.copy(errorMessage = null) }
            RecipeIntent.CheckModel -> checkModel()
            RecipeIntent.OpenModelManagement -> openModelManagement()
        }
    }

    private fun addCurrentInput() {
        val text = _state.value.input.trim()
        if (text.isBlank()) return
        val newIngredient = Ingredient(name = text)
        val alreadyPresent = _state.value.ingredients.any { it.name.equals(text, ignoreCase = true) }
        if (alreadyPresent) {
            viewModelScope.launch { _effects.send(RecipeEffect.ShowSnackbar("Ingredient already added")) }
            return
        }
        _state.update { it.copy(ingredients = it.ingredients + newIngredient, input = "") }
    }

    private fun removeIngredient(name: String) {
        _state.update { state ->
            state.copy(ingredients = state.ingredients.filterNot { it.name == name })
        }
    }

    private fun openModelManagement() {
        viewModelScope.launch { _effects.send(RecipeEffect.NavigateToModelManagement) }
    }

    /**
     * Resolve the currently selected model descriptor (or fall back to the
     * default) and check whether its file is on disk. Updates the state
     * accordingly. The engine is initialised only when the model is
     * installed.
     */
    private fun checkModel() {
        viewModelScope.launch {
            _state.update { it.copy(modelStatus = ModelStatus.Checking) }
            val descriptor = currentDescriptor()
            val installed = descriptor != null && withContext(dispatchers.io) {
                modelManager.isInstalled(descriptor)
            }
            _state.update {
                it.copy(
                    selectedModel = descriptor,
                    selectedModelInstalled = installed,
                )
            }
            if (descriptor == null) {
                _state.update { it.copy(modelStatus = ModelStatus.NoModelAvailable) }
                return@launch
            }
            if (!installed) {
                _state.update { it.copy(modelStatus = ModelStatus.NoModelAvailable) }
                return@launch
            }
            ensureEngineReady()
        }
    }

    private fun currentDescriptor(): LlmModelDescriptor? {
        val id = selectionStore.current()
        return LlmModelCatalog.findById(id)
    }

    private fun ensureEngineReady() {
        if (_state.value.modelStatus == ModelStatus.Ready) return
        viewModelScope.launch {
            val outcome = realEngine.initialize()
            when (outcome) {
                is Outcome.Success -> _state.update { it.copy(modelStatus = ModelStatus.Ready) }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        modelStatus = ModelStatus.Failed(
                            reason = outcome.error.message ?: "Engine init failed",
                        ),
                    )
                }
            }
        }
    }

    private fun startGeneration() {
        val current = _state.value
        if (!current.canGenerate) return
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            withContext(dispatchers.io) {
                _state.update {
                    it.copy(
                        isGenerating = true,
                        streamedText = "",
                        recipe = null,
                        errorMessage = null,
                    )
                }
                _effects.send(RecipeEffect.ScrollToOutput)
                try {
                    generateRecipe(realEngine, current.ingredients).collect { event ->
                        when (event) {
                            is RecipeEvent.Token -> _state.update {
                                it.copy(streamedText = it.streamedText + event.delta)
                            }
                            is RecipeEvent.Complete -> _state.update {
                                it.copy(isGenerating = false, recipe = event.recipe)
                            }
                            is RecipeEvent.Failed -> _state.update {
                                it.copy(
                                    isGenerating = false,
                                    errorMessage = event.error.message ?: "Generation failed",
                                )
                            }
                        }
                    }
                } finally {
                    _state.update { it.copy(isGenerating = false) }
                }
            }
        }
    }

    private fun cancelGeneration() {
        generationJob?.cancel()
        realEngine.cancel()
        _state.update { it.copy(isGenerating = false) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeSelection() {
        // Re-check the disk when EITHER the selected model changes OR the
        // download state for the currently selected model transitions to
        // SUCCEEDED. The second trigger is what fixes the
        // "downloaded-a-model-in-model-management, came back to the
        // recipe screen, still says NoModelAvailable" bug: for a
        // first-time user the default model is auto-selected, so
        // selectionStore.selectedModelId emits exactly once at init and
        // never re-emits when the user downloads that same default model.
        //
        // RUNNING / FAILED / CANCELLED transitions don't change whether
        // the file is on disk, so we skip those — checkModel() is
        // idempotent but it does I/O, and the recipe screen wouldn't
        // change its display state anyway.
        selectionStore.selectedModelId
            .distinctUntilChanged()
            .flatMapLatest { id ->
                // LlmModelSelectionStore.selectedModelId is declared
                // Flow<String?> even though the `?: DEFAULT_MODEL.id`
                // fallback means it's never actually null. Coerce to the
                // catalog id explicitly so progressStore.progressFor(...)
                // gets the non-null String it requires.
                val selectedId = id ?: LlmModelCatalog.DEFAULT_MODEL.id
                progressStore.progressFor(selectedId)
                    .map { selectedId to it?.state }
            }
            .distinctUntilChanged()
            .onEach { (_, state) ->
                if (state == null || state == DownloadState.SUCCEEDED) {
                    onIntent(RecipeIntent.CheckModel)
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        generationJob?.cancel()
        super.onCleared()
    }
}