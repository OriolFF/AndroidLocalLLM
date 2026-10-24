package com.llmlocal.feature.modelmanagement.domain

import com.llmlocal.core.model.LlmModelDescriptor
import kotlinx.coroutines.flow.Flow

/** The list of models the app knows about: curated built-ins plus discovered ones. */
interface ModelCatalogSource {
    /** Live list; re-emits after a discovery. */
    val models: Flow<List<LlmModelDescriptor>>

    /** Synchronous snapshot of [models]. */
    fun all(): List<LlmModelDescriptor>

    fun find(id: String): LlmModelDescriptor? = all().firstOrNull { it.id == id }
}

/** Result of asking the remote source for models. */
sealed interface DiscoveryResult {
    /** Discovery ran; [newModels] were not in the catalog before. */
    data class Updated(val newModels: Int) : DiscoveryResult

    /** The cache was fresh and nothing was fetched. */
    data object Skipped : DiscoveryResult

    data class Failed(val reason: String?) : DiscoveryResult
}

/** Finds more downloadable models (Hugging Face) and merges them into the catalog. */
interface ModelDiscoverySource {
    /** [force] ignores the staleness window. Never throws. */
    suspend fun discover(force: Boolean): DiscoveryResult
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
