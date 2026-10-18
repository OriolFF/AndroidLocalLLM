package com.llmlocal.core.llm.readiness

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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the "is the selected model ready to run?" state machine, shared by
 * every ViewModel that gates an LLM action on model availability.
 *
 * Pipeline:
 *
 * ```
 *   selectionStore.selectedModelId            (DataStore, distinct)
 *     combine recheck ticks                   (recheck() re-runs the chain)
 *     → flatMapLatest { id ->
 *         catalog miss                        → NoModel
 *         progressStore.progressFor(id)       (DataStore, distinct)
 *           RUNNING                           → Downloading(progress)
 *           anything else (null/IDLE/SUCCEEDED/FAILED/CANCELLED)
 *                                             → Checking
 *                                                → NoModel        (file missing)
 *                                                → Initializing → Ready | Failed
 *       }
 * ```
 *
 * The documented gotcha — "the default model is already selected on a
 * fresh install, so the selection flow never re-emits when the user
 * downloads that very model" — is handled by keying the disk check off
 * the per-model progress flow: when the worker writes `SUCCEEDED` the
 * progress flow emits, the chain re-checks the disk and initialises the
 * engine. Any non-`RUNNING` progress state (including `null`, i.e. never
 * downloaded) triggers a disk check, so the observer is also correct
 * after a failed or cancelled download (file deleted → `NoModel`).
 *
 * Disk and engine work run on [DispatcherProvider.io]. One observer
 * serves one collector scope; register it as a Koin `factory` and call
 * [observe] with the owning ViewModel's scope.
 *
 * The primary constructor takes the collaborators as flows and lambdas so
 * unit tests can drive it without a `Context` or DataStore. Production
 * code uses the secondary constructor, which adapts the real stores.
 */
class ModelReadinessObserver internal constructor(
    private val selectedModelId: Flow<String?>,
    private val progressFor: (modelId: String) -> Flow<PerModelProgress?>,
    private val isInstalled: suspend (LlmModelDescriptor) -> Boolean,
    private val engine: LlmEngine,
    private val dispatchers: DispatcherProvider,
    private val findDescriptor: (modelId: String) -> LlmModelDescriptor? = LlmModelCatalog::findById,
) {

    /** Production wiring over the DataStore-backed stores and the file manager. */
    constructor(
        selectionStore: LlmModelSelectionStore,
        progressStore: ModelDownloadProgressStore,
        manager: LlmModelManager,
        engine: LlmEngine,
        dispatchers: DispatcherProvider,
    ) : this(
        selectedModelId = selectionStore.selectedModelId,
        progressFor = progressStore::progressFor,
        isInstalled = manager::isInstalled,
        engine = engine,
        dispatchers = dispatchers,
    )

    private val _readiness = MutableStateFlow<ModelReadiness>(ModelReadiness.Checking)
    private val readiness: StateFlow<ModelReadiness> = _readiness.asStateFlow()
    private val recheckRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val started = AtomicBoolean(false)

    /**
     * Starts the observer under [scope] and returns its hot state. Only the
     * first call launches the collector; later calls return the same
     * [StateFlow]. The collector lives as long as [scope].
     */
    fun observe(scope: CoroutineScope): StateFlow<ModelReadiness> {
        if (started.compareAndSet(false, true)) {
            scope.launch {
                readinessFlow().collect { _readiness.value = it }
            }
        }
        return readiness
    }

    /**
     * Re-runs the disk + engine check for the current selection. Use it for
     * a "Retry" action after [ModelReadiness.Failed] or when the caller
     * knows the disk changed behind the stores' back. Returns as soon as
     * the request is queued; the new state arrives through [observe].
     */
    suspend fun recheck() {
        recheckRequests.emit(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun readinessFlow(): Flow<ModelReadiness> {
        val selection = selectedModelId
            .map { it ?: LlmModelCatalog.DEFAULT_MODEL.id }
            .distinctUntilChanged()
        val ticks = recheckRequests.onStart { emit(Unit) }
        return combine(selection, ticks) { id, _ -> id }
            .flatMapLatest { id -> readinessFor(id) }
    }

    private fun readinessFor(modelId: String): Flow<ModelReadiness> {
        val descriptor = findDescriptor(modelId) ?: return flowOf(ModelReadiness.NoModel)
        return progressFor(modelId)
            .distinctUntilChanged()
            .transform { progress ->
                if (progress?.state == DownloadState.RUNNING) {
                    emit(ModelReadiness.Downloading(progress))
                } else {
                    emit(ModelReadiness.Checking)
                    val installed = withContext(dispatchers.io) { isInstalled(descriptor) }
                    if (!installed) {
                        emit(ModelReadiness.NoModel)
                    } else {
                        emit(ModelReadiness.Initializing)
                        emit(initialize(descriptor))
                    }
                }
            }
    }

    private suspend fun initialize(descriptor: LlmModelDescriptor): ModelReadiness {
        val outcome = try {
            withContext(dispatchers.io) { engine.initialize() }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Outcome.Failure(t)
        }
        return when (outcome) {
            is Outcome.Success -> ModelReadiness.Ready(descriptor)
            is Outcome.Failure -> ModelReadiness.Failed(
                reason = outcome.error.message ?: DEFAULT_FAILURE_REASON,
                descriptor = descriptor,
            )
        }
    }

    private companion object {
        const val DEFAULT_FAILURE_REASON = "Engine init failed"
    }
}
