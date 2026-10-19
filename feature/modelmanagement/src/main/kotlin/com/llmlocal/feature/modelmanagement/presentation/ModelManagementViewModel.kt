package com.llmlocal.feature.modelmanagement.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.feature.modelmanagement.domain.ModelFilters
import com.llmlocal.feature.modelmanagement.domain.ModelListing
import com.llmlocal.feature.modelmanagement.domain.usecase.CancelDownloadUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.DownloadModelUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.FilterModelsUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.ObserveModelListingsUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.RemoveModelResult
import com.llmlocal.feature.modelmanagement.domain.usecase.RemoveModelUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.SelectModelResult
import com.llmlocal.feature.modelmanagement.domain.usecase.SelectModelUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.StartDownloadResult
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementEffect
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementIntent
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementLabels
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the model-management screen.
 *
 * Pure presentation logic: [ObserveModelListingsUseCase] supplies the
 * combined catalog / installed / selection / progress / job-status
 * stream, [FilterModelsUseCase] applies the user's [ModelFilters], and
 * this class folds both with the local UI bits (filters, details sheet,
 * error) into one [ModelManagementState]. The screen never re-derives
 * the filtered list.
 *
 * Snackbar copy comes from the caller-supplied [ModelManagementLabels]
 * (passed via Koin parameters from `ModelManagementRoute`), so this class
 * carries no brand strings.
 *
 * The view model does **not** itself run downloads — that work belongs to
 * `ModelDownloadWorker` so the OS can keep it alive across process death.
 */
class ModelManagementViewModel(
    observeListings: ObserveModelListingsUseCase,
    private val downloadModel: DownloadModelUseCase,
    private val cancelDownload: CancelDownloadUseCase,
    private val selectModel: SelectModelUseCase,
    private val removeModel: RemoveModelUseCase,
    private val filterModels: FilterModelsUseCase,
    private val dispatchers: DispatcherProvider,
    private val labels: ModelManagementLabels,
) : ViewModel() {

    /** UI-owned slice of the state; everything else comes from the listings. */
    private data class Local(
        val filters: ModelFilters = ModelFilters.DEFAULT,
        val lastError: String? = null,
        val detailsForId: String? = null,
    )

    private val local = MutableStateFlow(Local())
    private val refreshRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val state: StateFlow<ModelManagementState> =
        combine(
            observeListings(refreshRequests).flowOn(dispatchers.default),
            local,
        ) { listings, ui -> buildState(listings, ui) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ModelManagementState())

    private val _effects = Channel<ModelManagementEffect>(Channel.BUFFERED)
    val effects: Flow<ModelManagementEffect> = _effects.receiveAsFlow()

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
                local.update { it.copy(detailsForId = intent.modelId) }
            ModelManagementIntent.DismissDetails ->
                local.update { it.copy(detailsForId = null) }
            ModelManagementIntent.DismissError ->
                local.update { it.copy(lastError = null) }
            ModelManagementIntent.Refresh -> refreshRequests.tryEmit(Unit)
            is ModelManagementIntent.SetFilters ->
                local.update { it.copy(filters = intent.filters) }
            ModelManagementIntent.ClearFilters ->
                local.update { it.copy(filters = ModelFilters.DEFAULT) }
        }
    }

    private fun startDownload(modelId: String) {
        when (val result = downloadModel(modelId)) {
            StartDownloadResult.Started -> Unit
            is StartDownloadResult.UnknownModel ->
                local.update { it.copy(lastError = labels.unknownModelError(result.modelId)) }
        }
    }

    private fun remove(modelId: String) {
        viewModelScope.launch {
            val result = removeModel(modelId)
            if (result is RemoveModelResult.Removed) {
                _effects.send(
                    ModelManagementEffect.ShowSnackbar(
                        labels.modelRemovedSnackbar(result.descriptor.displayName),
                    ),
                )
            }
            refreshRequests.tryEmit(Unit)
        }
    }

    private fun select(modelId: String) {
        viewModelScope.launch {
            val message = when (val result = selectModel(modelId)) {
                is SelectModelResult.Selected ->
                    labels.modelSelectedSnackbar(result.descriptor.displayName)
                is SelectModelResult.NotInstalled ->
                    labels.modelNotDownloadedSnackbar(result.descriptor.displayName)
                SelectModelResult.UnknownModel -> return@launch
            }
            _effects.send(ModelManagementEffect.ShowSnackbar(message))
        }
    }

    private fun buildState(listings: List<ModelListing>, ui: Local) = ModelManagementState(
        catalog = listings.map { it.descriptor },
        installedIds = listings.filter { it.installed }.map { it.id }.toSet(),
        selectedId = listings.firstOrNull { it.selected }?.id,
        downloading = listings
            .filter { it.downloading }
            .associate { it.id to checkNotNull(it.progress) },
        filters = ui.filters,
        filteredCatalog = filterModels(listings, ui.filters).map { it.descriptor },
        lastError = ui.lastError,
        detailsForId = ui.detailsForId,
    )
}
