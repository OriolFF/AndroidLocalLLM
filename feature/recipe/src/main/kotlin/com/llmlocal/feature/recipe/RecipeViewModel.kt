package com.llmlocal.feature.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.domain.model.RecipeEvent
import com.llmlocal.core.domain.usecase.GenerateRecipeUseCase
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.model.Ingredient
import com.llmlocal.feature.recipe.mvi.ModelStatus
import com.llmlocal.feature.recipe.mvi.RecipeEffect
import com.llmlocal.feature.recipe.mvi.RecipeIntent
import com.llmlocal.feature.recipe.mvi.RecipeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Heavy work (model download, LLM generation) is launched on
 * [viewModelScope] and runs on the [DispatcherProvider.io] dispatcher.
 *
 * The [LlmEngine] injected here is the real on-device engine; the demo
 * engine is injected separately and swapped in/out via the
 * [RecipeIntent.SetUseDemoEngine] intent.
 */
class RecipeViewModel(
    private val generateRecipe: GenerateRecipeUseCase,
    private val modelManager: LlmModelManager,
    private val realEngine: LlmEngine,
    private val demoEngine: LlmEngine,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RecipeState(
            modelHumanSize = LlmModelManager.humanReadableBytes(modelManager.descriptor().sizeBytes),
            modelFilename = modelManager.descriptor().filename,
        )
    )
    val state: StateFlow<RecipeState> = _state.asStateFlow()

    private val _effects = Channel<RecipeEffect>(Channel.BUFFERED)
    val effects: Flow<RecipeEffect> = _effects.receiveAsFlow()

    private var generationJob: Job? = null
    private var downloadJob: Job? = null

    init {
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
            RecipeIntent.RetryModelDownload -> retryDownload()
            RecipeIntent.CancelModelDownload -> cancelDownload()
            is RecipeIntent.SetUseDemoEngine -> setUseDemoEngine(intent.enabled)
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

    private fun checkModel() {
        viewModelScope.launch {
            if (_state.value.useDemoEngine) return@launch
            _state.update { it.copy(modelStatus = ModelStatus.Checking) }
            val available = modelManager.isModelAvailable()
            if (available) {
                ensureEngineReady()
            } else {
                _state.update {
                    it.copy(modelStatus = ModelStatus.Unknown)
                }
            }
        }
    }

    /**
     * Immediately updates the UI to "Downloading" so the user gets instant
     * feedback, then launches the actual download. Real progress is reported
     * via [ModelStatus.DownloadProgress] with bytes / total / speed / ETA.
     */
    private fun retryDownload() {
        if (_state.value.useDemoEngine) {
            viewModelScope.launch { _effects.send(RecipeEffect.ShowSnackbar("Demo mode is on — no download needed.")) }
            return
        }
        // Cancel any in-flight download.
        downloadJob?.cancel()
        // INSTANT feedback: flip to "Downloading" before we even start the
        // coroutine that actually fetches bytes. Without this, the button
        // press looks dead for ~250 ms while OkHttp warms up.
        _state.update { it.copy(modelStatus = ModelStatus.Downloading, errorMessage = null) }
        downloadJob = viewModelScope.launch {
            try {
                modelManager.ensureModel().collect { progress ->
                    _state.update {
                        it.copy(
                            modelStatus = ModelStatus.DownloadProgress(
                                bytesRead = progress.bytesRead,
                                totalBytes = progress.totalBytes,
                                percent = progress.percent,
                                speedBytesPerSec = progress.speedBytesPerSec,
                                etaSeconds = progress.etaSeconds,
                            )
                        )
                    }
                }
                ensureEngineReady()
            } catch (t: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(modelStatus = ModelStatus.Unknown) }
                throw t
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        modelStatus = ModelStatus.Failed(
                            reason = t.message ?: "Download failed",
                        )
                    )
                }
            }
        }
    }

    private fun cancelDownload() {
        val job = downloadJob ?: return
        viewModelScope.launch {
            job.cancelAndJoin()
            _state.update { it.copy(modelStatus = ModelStatus.Unknown) }
        }
    }

    private suspend fun ensureEngineReady() {
        val outcome = realEngine.initialize()
        when (outcome) {
            is com.llmlocal.core.common.result.Outcome.Success -> {
                _state.update { it.copy(modelStatus = ModelStatus.Ready) }
            }
            is com.llmlocal.core.common.result.Outcome.Failure -> {
                _state.update {
                    it.copy(modelStatus = ModelStatus.Failed(reason = outcome.error.message ?: "Engine init failed"))
                }
            }
        }
    }

    private fun setUseDemoEngine(enabled: Boolean) {
        if (_state.value.useDemoEngine == enabled) return
        if (enabled) {
            // Cancel any in-flight download — Demo mode doesn't need it.
            downloadJob?.cancel()
            downloadJob = null
        }
        _state.update {
            it.copy(
                useDemoEngine = enabled,
                // When switching back to real mode, the banner shows
                // "Unknown" so the user can trigger a fresh check.
                modelStatus = if (enabled) ModelStatus.NotSelected else ModelStatus.Unknown,
                errorMessage = null,
                streamedText = "",
                recipe = null,
            )
        }
        if (!enabled) onIntent(RecipeIntent.CheckModel)
    }

    private fun startGeneration() {
        val current = _state.value
        if (!current.canGenerate) return
        generationJob?.cancel()
        val engine = if (current.useDemoEngine) demoEngine else realEngine
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
                    generateRecipe(engine, current.ingredients).collect { event ->
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
        demoEngine.cancel()
        _state.update { it.copy(isGenerating = false) }
    }

    override fun onCleared() {
        generationJob?.cancel()
        downloadJob?.cancel()
        super.onCleared()
    }
}
