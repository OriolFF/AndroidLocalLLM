package com.llmlocal.feature.modelmanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.llm.download.ModelDownloadProgressStore
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.core.llm.selection.LlmModelSelectionStore
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.feature.modelmanagement.di.ModelDownloadScheduler
import com.llmlocal.feature.modelmanagement.di.WorkInfoObserver
import com.llmlocal.feature.modelmanagement.mvi.ModelFilters
import com.llmlocal.feature.modelmanagement.mvi.ModelManagementEffect
import com.llmlocal.feature.modelmanagement.mvi.ModelManagementIntent
import com.llmlocal.feature.modelmanagement.mvi.ModelManagementState
import com.llmlocal.feature.modelmanagement.mvi.QuantFilter
import com.llmlocal.feature.modelmanagement.mvi.StatusFilter
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
 * MVI ViewModel for the model-management screen.
 *
 * Pulls three reactive sources together into [ModelManagementState]:
 *  1. [ModelDownloadProgressStore.progress] — per-model live progress.
 *  2. [LlmModelSelectionStore.selectedModelId] — currently selected model.
 *  3. [WorkInfoObserver.observe] — terminal states (SUCCEEDED / FAILED /
 *     CANCELLED) for download jobs.
 *
 * The view model also owns the user's filter set ([ModelFilters]) and
 * computes [ModelManagementState.filteredCatalog] from
 * `catalog + installedIds + downloading + filters` atomically — the
 * screen never re-derives it.
 *
 * On init it scans disk via [LlmModelManager.installedModelIds] so the
 * catalog can render Installed / Not installed badges correctly.
 *
 * The view model does **not** itself run downloads — that work belongs to
 * [ModelDownloadWorker] so the OS can keep it alive across process death.
 */
class ModelManagementViewModel(
    private val manager: LlmModelManager,
    private val selectionStore: LlmModelSelectionStore,
    private val progressStore: ModelDownloadProgressStore,
    private val scheduler: ModelDownloadScheduler,
    private val workInfoObserver: WorkInfoObserver,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ModelManagementState(catalog = LlmModelCatalog.ALL),
    )
    val state: StateFlow<ModelManagementState> = _state.asStateFlow()

    private val _effects = Channel<ModelManagementEffect>(Channel.BUFFERED)
    val effects: Flow<ModelManagementEffect> = _effects.receiveAsFlow()

    init {
        observeProgress()
        observeSelection()
        observeWorkInfo()
        refreshInstalledSet()
    }

    /**
     * Single dispatch point for all user actions. The pattern keeps the
     * UI declarative: `viewModel.onIntent(ModelManagementIntent.X(...))`.
     */
    fun onIntent(intent: ModelManagementIntent) {
        when (intent) {
            is ModelManagementIntent.StartDownload -> startDownload(intent.modelId)
            is ModelManagementIntent.CancelDownload -> cancelDownload(intent.modelId)
            is ModelManagementIntent.Remove -> remove(intent.modelId)
            is ModelManagementIntent.Select -> select(intent.modelId)
            is ModelManagementIntent.ShowDetails ->
                _state.update { it.copy(detailsForId = intent.modelId) }
            ModelManagementIntent.DismissDetails ->
                _state.update { it.copy(detailsForId = null) }
            ModelManagementIntent.DismissError ->
                _state.update { it.copy(lastError = null) }
            ModelManagementIntent.Refresh -> refreshInstalledSet()
            is ModelManagementIntent.SetFilters ->
                _state.update { it.copy(filters = intent.filters).recompute() }
            ModelManagementIntent.ClearFilters ->
                _state.update { it.copy(filters = ModelFilters.DEFAULT).recompute() }
        }
    }

    private fun startDownload(modelId: String) {
        val descriptor = LlmModelCatalog.findById(modelId)
        if (descriptor == null) {
            _state.update { it.copy(lastError = "Unknown model: $modelId") }
            return
        }
        scheduler.enqueue(descriptor)
    }

    private fun cancelDownload(modelId: String) {
        scheduler.cancel(modelId)
    }

    private fun remove(modelId: String) {
        val descriptor = LlmModelCatalog.findById(modelId) ?: return
        viewModelScope.launch {
            val removed = withContext(dispatchers.io) { manager.remove(descriptor) }
            if (removed) {
                _effects.send(ModelManagementEffect.ShowSnackbar("${descriptor.displayName} removed"))
            }
            // The selection store is left as-is; the user can clear it
            // manually if they want. The engine will surface a "file
            // missing" failure on next initialize() which is the right
            // signal.
            refreshInstalledSet()
        }
    }

    private fun select(modelId: String) {
        val descriptor = LlmModelCatalog.findById(modelId) ?: return
        viewModelScope.launch {
            if (modelId !in _state.value.installedIds) {
                _effects.send(
                    ModelManagementEffect.ShowSnackbar(
                        "${descriptor.displayName} is not downloaded yet.",
                    ),
                )
                return@launch
            }
            selectionStore.setSelected(modelId)
            _effects.send(
                ModelManagementEffect.ShowSnackbar(
                    "${descriptor.displayName} selected for recipe generation",
                ),
            )
        }
    }

    private fun refreshInstalledSet() {
        viewModelScope.launch {
            val ids = withContext(dispatchers.io) { manager.installedModelIds() }
            _state.update { it.copy(installedIds = ids).recompute() }
        }
    }

    private fun observeProgress() {
        progressStore.progress
            .onEach { snapshot ->
                _state.update { current ->
                    current
                        .copy(
                            downloading = snapshot.filterValues {
                                it.state == DownloadState.RUNNING
                            },
                        )
                        .recompute()
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeSelection() {
        selectionStore.selectedModelId
            .onEach { id -> _state.update { it.copy(selectedId = id) } }
            .launchIn(viewModelScope)
    }

    private fun observeWorkInfo() {
        // When a download transitions to SUCCEEDED, the worker has already
        // called progressStore.markInstalled — we just need to refresh the
        // installed set so the Installed badge shows up immediately.
        workInfoObserver.observe(scheduler.workTag())
            .onEach { infos ->
                val anyFinished = infos.any {
                    it.state.isFinished && it.state == WorkInfo.State.SUCCEEDED
                }
                if (anyFinished) refreshInstalledSet()
            }
            .launchIn(viewModelScope)
    }

    /**
     * Returns a copy of this state with [filteredCatalog] recomputed from
     * the current [catalog] / [installedIds] / [downloading] / [filters].
     * Pure function — safe to call inside `_state.update { ... }`.
     */
    private fun ModelManagementState.recompute(): ModelManagementState =
        copy(filteredCatalog = applyFilters(catalog, installedIds, downloading, filters))
}

/**
 * Pure filter function — kept top-level (not a member) so it can be unit
 * tested in isolation and reasoned about without going through the view
 * model. The contract:
 *
 *  1. Status filter:
 *      - `ALL`         — every descriptor is kept.
 *      - `INSTALLED`   — only descriptors whose id is in [installedIds].
 *      - `AVAILABLE`   — only descriptors not in [installedIds] and not
 *                        currently downloading.
 *      - `DOWNLOADING` — only descriptors in [downloading].
 *  2. Family filter — single-select; `null` means "all families".
 *  3. Size filter — single-select; `null` means "all sizes".
 *  4. Quantisation filter — derived from the descriptor's tags; `ALL`
 *      means "either is fine".
 *
 * Filters compose with **AND** semantics. An item passes only if it
 * satisfies every active filter.
 */
internal fun applyFilters(
    catalog: List<LlmModelDescriptor>,
    installedIds: Set<String>,
    downloading: Map<String, PerModelProgress>,
    filters: ModelFilters,
): List<LlmModelDescriptor> = catalog.filter { d ->
    val statusOk = when (filters.status) {
        StatusFilter.ALL -> true
        StatusFilter.INSTALLED -> d.id in installedIds
        StatusFilter.AVAILABLE -> d.id !in installedIds && d.id !in downloading
        StatusFilter.DOWNLOADING -> d.id in downloading
    }
    val familyOk = filters.family == null || d.family == filters.family
    val sizeOk = filters.sizeBucket == null ||
        ModelFilters.bucketFor(d.sizeBytes) == filters.sizeBucket
    val quantOk = when (filters.quant) {
        QuantFilter.ALL -> true
        QuantFilter.QUANTISED -> ModelFilters.isQuantised(d)
        QuantFilter.FULL_PRECISION -> !ModelFilters.isQuantised(d)
    }
    statusOk && familyOk && sizeOk && quantOk
}
