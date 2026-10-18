// TEMP shim: replaced by WS3 at merge.
// WS3 lands `com.llmlocal.core.llm.readiness.{ModelReadiness, ModelReadinessObserver}`
// in `:core:llm`. The lead swaps the imports in this feature, binds
// `ReadinessSource` to WS3's observer (or deletes `ReadinessSource` and
// injects the observer directly) and removes this file.
package com.llmlocal.feature.webclipper.readiness

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.llm.download.ModelDownloadProgressStore
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.core.llm.selection.LlmModelSelectionStore
import com.llmlocal.core.model.LlmModelDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

/**
 * Whether the selected on-device model can run right now. Mirrors plan
 * §2.4 one-to-one so the WS3 type can replace it without touching callers.
 */
sealed interface ModelReadiness {
    /** No model selected, or the selected one is not on disk. */
    data object NoModel : ModelReadiness

    /** Looking at the disk / store. */
    data object Checking : ModelReadiness

    /** The selected model is being downloaded. */
    data class Downloading(val progress: PerModelProgress) : ModelReadiness

    /** File present; the engine is loading it. */
    data object Initializing : ModelReadiness

    /** The engine is loaded with [descriptor]. */
    data class Ready(val descriptor: LlmModelDescriptor) : ModelReadiness

    /** Engine init failed for [descriptor] (or no descriptor could be resolved). */
    data class Failed(val reason: String, val descriptor: LlmModelDescriptor?) : ModelReadiness
}

/**
 * Seam the browser ViewModel observes readiness through. WS3's
 * `ModelReadinessObserver.observe(scope)` has the same shape, so the lead
 * can bind it here or inject it directly.
 */
fun interface ReadinessSource {
    fun observe(scope: CoroutineScope): StateFlow<ModelReadiness>
}

/**
 * Minimal readiness pipeline: selection → download progress for that id →
 * installed on disk → `engine.initialize()`. Same chain as
 * `LlmTaskViewModel.observeSelection()` + `checkModel()`, expressed as one
 * flow so the browser can render the clip button from it.
 */
class ShimModelReadinessObserver(
    private val selectionStore: LlmModelSelectionStore,
    private val progressStore: ModelDownloadProgressStore,
    private val manager: LlmModelManager,
    private val engine: LlmEngine,
    private val dispatchers: DispatcherProvider,
) : ReadinessSource {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(scope: CoroutineScope): StateFlow<ModelReadiness> =
        selectionStore.selectedModelId
            .map { it ?: LlmModelCatalog.DEFAULT_MODEL.id }
            .distinctUntilChanged()
            .flatMapLatest { id ->
                progressStore.progressFor(id).map { id to it }
            }
            .distinctUntilChanged()
            .transformLatest { (id, progress) ->
                emit(ModelReadiness.Checking)
                val descriptor = LlmModelCatalog.findById(id)
                if (descriptor == null) {
                    emit(ModelReadiness.NoModel)
                    return@transformLatest
                }
                if (progress?.state == DownloadState.RUNNING) {
                    emit(ModelReadiness.Downloading(progress))
                    return@transformLatest
                }
                val installed = withContext(dispatchers.io) { manager.isInstalled(descriptor) }
                if (!installed) {
                    emit(ModelReadiness.NoModel)
                    return@transformLatest
                }
                emit(ModelReadiness.Initializing)
                when (val outcome = withContext(dispatchers.io) { engine.initialize() }) {
                    is Outcome.Success -> emit(ModelReadiness.Ready(descriptor))
                    is Outcome.Failure -> emit(
                        ModelReadiness.Failed(
                            reason = outcome.error.message ?: "Engine init failed",
                            descriptor = descriptor,
                        ),
                    )
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, ModelReadiness.Checking)
}
