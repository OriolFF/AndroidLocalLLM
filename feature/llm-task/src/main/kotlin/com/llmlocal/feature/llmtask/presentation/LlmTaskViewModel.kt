package com.llmlocal.feature.llmtask.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.feature.llmtask.domain.ModelAvailability
import com.llmlocal.feature.llmtask.domain.usecase.CancelGenerationUseCase
import com.llmlocal.feature.llmtask.domain.usecase.GenerateTaskUseCase
import com.llmlocal.feature.llmtask.domain.usecase.ObserveModelReadinessUseCase
import com.llmlocal.feature.llmtask.domain.usecase.ObserveSelectedModelUseCase
import com.llmlocal.feature.llmtask.domain.usecase.RecheckModelUseCase
import com.llmlocal.feature.llmtask.presentation.mvi.LlmTaskEffect
import com.llmlocal.feature.llmtask.presentation.mvi.LlmTaskIntent
import com.llmlocal.feature.llmtask.presentation.mvi.LlmTaskLabels
import com.llmlocal.feature.llmtask.presentation.mvi.LlmTaskState
import com.llmlocal.feature.llmtask.presentation.mvi.ModelStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Generic LLM-task MVI ViewModel.
 *
 * Owns the [LlmTaskState] and processes [LlmTaskIntent]s dispatched by the
 * composable. The MVI loop is intentionally simple:
 *
 *  intent -> handler -> state update (+ optional effect)
 *
 * Heavy work (LLM generation) is launched on [viewModelScope] and runs on
 * the [DispatcherProvider.io] dispatcher. Model readiness — selection →
 * download → disk → engine initialise — is observed through
 * [ObserveModelReadinessUseCase]; this ViewModel maps the domain
 * [ModelAvailability] states into the feature-facing [ModelStatus].
 *
 * The result type is stored as `Any?` in [LlmTaskState.result] — the
 * consuming screen casts it to the app-specific domain type via its
 * `renderOutput: @Composable (Any?) -> Unit` lambda.
 *
 * @param config the app-specific `LlmTaskConfig<*>` (prompt builder + parser)
 *   the use case will use to format and parse the LLM response.
 * @param labels UI copy used for snackbars (e.g. duplicate-input warnings).
 *   The screen consumes its own labels via [LlmTaskLabels] parameter.
 */
class LlmTaskViewModel(
    private val observeReadiness: ObserveModelReadinessUseCase,
    private val observeSelectedModel: ObserveSelectedModelUseCase,
    private val recheckModel: RecheckModelUseCase,
    private val generateTask: GenerateTaskUseCase,
    private val cancelGeneration: CancelGenerationUseCase,
    private val dispatchers: DispatcherProvider,
    private val config: LlmTaskConfig<*>,
    private val labels: LlmTaskLabels,
) : ViewModel() {

    private val _state = MutableStateFlow(LlmTaskState())
    val state: StateFlow<LlmTaskState> = _state.asStateFlow()

    private val _effects = Channel<LlmTaskEffect>(Channel.BUFFERED)
    val effects: Flow<LlmTaskEffect> = _effects.receiveAsFlow()

    private var generationJob: Job? = null

    init {
        observeSelection()
        observeReadinessState()
    }

    /**
     * Single dispatch point for all user actions. The pattern lets the UI
     * stay declarative — `viewModel.onIntent(LlmTaskIntent.Generate)`.
     */
    fun onIntent(intent: LlmTaskIntent) {
        when (intent) {
            is LlmTaskIntent.UpdateInput -> _state.update { it.copy(input = intent.text) }
            LlmTaskIntent.AddInput -> addCurrentInput()
            is LlmTaskIntent.RemoveInput -> removeInput(intent.text)
            LlmTaskIntent.ClearInputs -> _state.update {
                it.copy(inputs = emptyList(), result = null, streamedText = "")
            }
            LlmTaskIntent.Generate -> startGeneration()
            LlmTaskIntent.CancelGeneration -> cancel()
            LlmTaskIntent.DismissError -> _state.update { it.copy(errorMessage = null) }
            LlmTaskIntent.CheckModel -> recheck()
            LlmTaskIntent.OpenModelManagement -> openModelManagement()
        }
    }

    private fun addCurrentInput() {
        val text = _state.value.input.trim()
        if (text.isBlank()) return
        val alreadyPresent = _state.value.inputs.any { it.equals(text, ignoreCase = true) }
        if (alreadyPresent) {
            viewModelScope.launch { _effects.send(LlmTaskEffect.ShowSnackbar(labels.duplicateInputSnackbar)) }
            return
        }
        _state.update { it.copy(inputs = it.inputs + text, input = "") }
    }

    private fun removeInput(text: String) {
        _state.update { state ->
            state.copy(inputs = state.inputs.filterNot { it == text })
        }
    }

    private fun openModelManagement() {
        viewModelScope.launch { _effects.send(LlmTaskEffect.NavigateToModelManagement) }
    }

    /**
     * Forwards the shared readiness state into [LlmTaskState.modelStatus].
     *
     * [ModelAvailability.Downloading] deliberately maps to
     * [ModelStatus.NoModelAvailable]: the task screen only renders the
     * no-model banner (which routes to the model screen, where the
     * progress is shown) and the Generate button's enabled state, so this
     * keeps the screen exactly as it behaved before the observer existed.
     */
    private fun observeReadinessState() {
        observeReadiness(viewModelScope)
            .onEach { readiness ->
                _state.update {
                    it.copy(
                        modelStatus = readiness.toModelStatus(),
                        selectedModelInstalled = readiness.isInstalled(),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun ModelAvailability.toModelStatus(): ModelStatus = when (this) {
        ModelAvailability.Checking -> ModelStatus.Checking
        ModelAvailability.NoModel -> ModelStatus.NoModelAvailable
        ModelAvailability.Downloading -> ModelStatus.NoModelAvailable
        ModelAvailability.Initializing -> ModelStatus.Checking
        ModelAvailability.Ready -> ModelStatus.Ready
        is ModelAvailability.Failed -> ModelStatus.Failed(reason = reason)
    }

    /** The file is on disk when the engine is ready, or when it was there but failed to load. */
    private fun ModelAvailability.isInstalled(): Boolean = when (this) {
        ModelAvailability.Ready -> true
        is ModelAvailability.Failed -> installed
        ModelAvailability.Checking,
        ModelAvailability.NoModel,
        ModelAvailability.Initializing,
        ModelAvailability.Downloading -> false
    }

    /**
     * Keeps [LlmTaskState.selectedModel] in step with the selection store so
     * the no-model banner can name the selected model before any download
     * or engine work has happened.
     */
    private fun observeSelection() {
        observeSelectedModel()
            .onEach { descriptor -> _state.update { it.copy(selectedModel = descriptor) } }
            .launchIn(viewModelScope)
    }

    /** `CheckModel` intent: ask the observer to re-run the disk + engine check. */
    private fun recheck() {
        viewModelScope.launch { recheckModel() }
    }

    @Suppress("UNCHECKED_CAST")
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
                        result = null,
                        errorMessage = null,
                    )
                }
                _effects.send(LlmTaskEffect.ScrollToOutput)
                try {
                    // config is LlmTaskConfig<*>; the use case takes LlmTaskConfig<T>
                    // for its type parameter. Erasure means we can safely cast to
                    // LlmTaskConfig<Any?> here — the parser returns Any? at runtime
                    // and the consuming screen casts back to its domain type.
                    generateTask(
                        config as LlmTaskConfig<Any?>,
                        current.inputs,
                    ).collect { event ->
                        when (event) {
                            is LlmTaskEvent.Token -> _state.update {
                                it.copy(streamedText = it.streamedText + event.delta)
                            }
                            is LlmTaskEvent.Complete<*> -> _state.update {
                                @Suppress("UNCHECKED_CAST")
                                it.copy(
                                    isGenerating = false,
                                    result = event.result as Any?,
                                )
                            }
                            is LlmTaskEvent.Failed -> _state.update {
                                it.copy(
                                    isGenerating = false,
                                    errorMessage = event.error.message ?: labels.generationFailedMessage,
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

    private fun cancel() {
        generationJob?.cancel()
        cancelGeneration()
        _state.update { it.copy(isGenerating = false) }
    }

    override fun onCleared() {
        generationJob?.cancel()
        super.onCleared()
    }
}
