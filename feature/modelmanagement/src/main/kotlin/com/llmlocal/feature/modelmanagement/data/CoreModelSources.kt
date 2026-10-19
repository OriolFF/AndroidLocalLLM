package com.llmlocal.feature.modelmanagement.data

import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.llm.download.ModelDownloadProgressStore
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.core.llm.selection.LlmModelSelectionStore
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.feature.modelmanagement.domain.DownloadProgress
import com.llmlocal.feature.modelmanagement.domain.DownloadProgressSource
import com.llmlocal.feature.modelmanagement.domain.DownloadStatus
import com.llmlocal.feature.modelmanagement.domain.InstalledModelsSource
import com.llmlocal.feature.modelmanagement.domain.ModelCatalogSource
import com.llmlocal.feature.modelmanagement.domain.ModelSelectionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [ModelCatalogSource] over the shared [LlmModelCatalog]. */
class LlmModelCatalogSource : ModelCatalogSource {
    override fun all(): List<LlmModelDescriptor> = LlmModelCatalog.ALL

    override fun find(id: String): LlmModelDescriptor? = LlmModelCatalog.findById(id)
}

/** [InstalledModelsSource] over [LlmModelManager] (files in `filesDir/llm/`). */
class LlmModelInstalledSource(
    private val manager: LlmModelManager,
    private val catalog: ModelCatalogSource,
) : InstalledModelsSource {
    override suspend fun installedIds(): Set<String> = manager.installedModelIds()

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
