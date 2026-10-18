package com.llmlocal.recipekeeper.library

import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.llm.download.ModelDownloadProgressStore
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.core.llm.selection.LlmModelSelectionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// TEMP shim: replaced by WS3 at merge. WS3 lands
// `com.llmlocal.core.llm.readiness.{ModelReadiness, ModelReadinessObserver}`;
// the lead then binds `ModelStatusSource` to an adapter over
// `ModelReadinessObserver.observe(scope)` (NoModel/Checking/Failed → NoModel,
// Downloading(progress) → Downloading(progress), Initializing/Ready → Ready)
// and deletes `StoreBackedModelStatusSource`.

/**
 * The only readiness signal the library screen needs.
 *
 *  - [NoModel]     — the selected model is not on disk: show the hero that
 *                    sends the user to model management.
 *  - [Downloading] — a download for the selected model is RUNNING: show the
 *                    compact progress card instead of the hero.
 *  - [Ready]       — the file is installed: no banner.
 */
sealed interface LibraryModelStatus {
    data object NoModel : LibraryModelStatus

    data class Downloading(val progress: PerModelProgress) : LibraryModelStatus {
        /** 0–100, or 0 while the total size is still unknown. */
        val percent: Int
            get() = progress.totalBytes?.takeIf { it > 0 }
                ?.let { ((progress.bytesRead * 100) / it).toInt().coerceIn(0, 100) }
                ?: 0
    }

    data object Ready : LibraryModelStatus
}

/** Hot source of [LibraryModelStatus]; bound as a Koin `single` in `recipeKeeperUiModule`. */
interface ModelStatusSource {
    val status: StateFlow<LibraryModelStatus>
}

/** In-memory source for previews and ViewModel tests. */
class FakeModelStatusSource(
    initial: LibraryModelStatus = LibraryModelStatus.Ready,
) : ModelStatusSource {
    override val status: MutableStateFlow<LibraryModelStatus> = MutableStateFlow(initial)
}

/**
 * Bridges `:core:llm`'s selection + download-progress stores and the model
 * manager into a [LibraryModelStatus] flow.
 *
 * Re-evaluates whenever the selected model changes **or** the download
 * state for that model changes, so a fresh install whose default model is
 * already "selected" still flips to [LibraryModelStatus.Ready] when the
 * download finishes (the same trap `LlmTaskViewModel.observeSelection`
 * guards against).
 */
// TEMP shim: replaced by WS3 at merge (ModelReadinessObserver).
class StoreBackedModelStatusSource(
    private val selectionStore: LlmModelSelectionStore,
    private val progressStore: ModelDownloadProgressStore,
    private val modelManager: LlmModelManager,
    scope: CoroutineScope,
) : ModelStatusSource {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val status: StateFlow<LibraryModelStatus> = selectionStore.selectedModelId
        .map { it ?: LlmModelCatalog.DEFAULT_MODEL.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            val descriptor = LlmModelCatalog.findById(id)
            progressStore.progressFor(id).map { progress ->
                when {
                    descriptor == null -> LibraryModelStatus.NoModel
                    progress?.state == DownloadState.RUNNING -> LibraryModelStatus.Downloading(progress)
                    modelManager.isInstalled(descriptor) -> LibraryModelStatus.Ready
                    else -> LibraryModelStatus.NoModel
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, LibraryModelStatus.NoModel)
}
