package com.llmlocal.feature.modelmanagement.domain

import com.llmlocal.core.model.LlmModelDescriptor
import kotlinx.coroutines.flow.Flow

/** The list of models the app knows about. */
interface ModelCatalogSource {
    fun all(): List<LlmModelDescriptor>

    fun find(id: String): LlmModelDescriptor? = all().firstOrNull { it.id == id }
}

/** Models whose files are present on disk. */
interface InstalledModelsSource {
    suspend fun installedIds(): Set<String>

    /** Deletes the model file; `true` if something was removed. */
    suspend fun remove(id: String): Boolean
}

/** Which model the engine should load. */
interface ModelSelectionSource {
    val selectedId: Flow<String?>

    suspend fun select(id: String)
}

/** Live per-model download snapshots, keyed by model id. */
interface DownloadProgressSource {
    val progress: Flow<Map<String, DownloadProgress>>
}

/** Starts and stops background downloads. */
interface DownloadScheduler {
    fun enqueue(id: String)

    fun cancel(id: String)
}

/** Status of every scheduled download job; re-emits on any change. */
interface DownloadWorkSource {
    fun observe(): Flow<List<DownloadWorkStatus>>
}
