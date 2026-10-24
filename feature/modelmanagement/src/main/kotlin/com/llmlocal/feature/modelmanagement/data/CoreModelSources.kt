package com.llmlocal.feature.modelmanagement.data

import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.llm.download.ModelDownloadProgressStore
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.llm.model.catalog.ModelCatalogRepository
import com.llmlocal.core.llm.selection.LlmModelSelectionStore
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.feature.modelmanagement.domain.DownloadProgress
import com.llmlocal.feature.modelmanagement.domain.DownloadProgressSource
import com.llmlocal.feature.modelmanagement.domain.DiscoveryResult
import com.llmlocal.feature.modelmanagement.domain.DownloadStatus
import com.llmlocal.feature.modelmanagement.domain.InstalledModelsSource
import com.llmlocal.feature.modelmanagement.domain.ModelCatalogSource
import com.llmlocal.feature.modelmanagement.domain.ModelDiscoverySource
import com.llmlocal.feature.modelmanagement.domain.ModelSelectionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [ModelCatalogSource] + [ModelDiscoverySource] over the shared [ModelCatalogRepository]. */
class LlmModelCatalogSource(
    private val repository: ModelCatalogRepository,
) : ModelCatalogSource, ModelDiscoverySource {
    override val models: Flow<List<LlmModelDescriptor>> = repository.models

    override fun all(): List<LlmModelDescriptor> = repository.current()

    override fun find(id: String): LlmModelDescriptor? = repository.findById(id)

    override suspend fun discover(force: Boolean): DiscoveryResult =
        when (val outcome = repository.refresh(force)) {
            is Outcome.Success ->
                if (outcome.value.skipped) DiscoveryResult.Skipped else DiscoveryResult.Updated(outcome.value.newModels)
            is Outcome.Failure -> DiscoveryResult.Failed(outcome.error.message)
        }
}

/** [InstalledModelsSource] over [LlmModelManager] (files in `filesDir/llm/`). */
class LlmModelInstalledSource(
    private val manager: LlmModelManager,
    private val catalog: ModelCatalogSource,
) : InstalledModelsSource {
    override suspend fun installedIds(): Set<String> = manager.installedModelIds(catalog.all())

    override suspend fun remove(id: String): Boolean {
        val descriptor = catalog.find(id) ?: return false
        return manager.remove(descriptor)
    }
}

/** [ModelSelectionSource] over the DataStore-backed [LlmModelSelectionStore]. */
class DataStoreModelSelectionSource(
    private val store: LlmModelSelectionStore,
) : ModelSelectionSource {
    override val selectedId: Flow<String?> = store.selectedModelId

    override suspend fun select(id: String) = store.setSelected(id)
}

/** [DownloadProgressSource] over [ModelDownloadProgressStore], mapping to domain types. */
class StoreDownloadProgressSource(
    store: ModelDownloadProgressStore,
) : DownloadProgressSource {
    override val progress: Flow<Map<String, DownloadProgress>> =
        store.progress.map { snapshot -> snapshot.mapValues { it.value.toDomain() } }
}

internal fun PerModelProgress.toDomain(): DownloadProgress = DownloadProgress(
    modelId = modelId,
    status = when (state) {
        DownloadState.IDLE -> DownloadStatus.IDLE
        DownloadState.RUNNING -> DownloadStatus.RUNNING
        DownloadState.SUCCEEDED -> DownloadStatus.SUCCEEDED
        DownloadState.FAILED -> DownloadStatus.FAILED
        DownloadState.CANCELLED -> DownloadStatus.CANCELLED
    },
    bytesRead = bytesRead,
    totalBytes = totalBytes,
    speedBytesPerSec = speedBytesPerSec,
    etaSeconds = etaSeconds,
    failureReason = failureReason,
)
