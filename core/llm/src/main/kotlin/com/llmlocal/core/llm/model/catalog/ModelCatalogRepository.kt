package com.llmlocal.core.llm.model.catalog

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.core.llm.model.discovery.ModelDiscoverySource
import com.llmlocal.core.model.LlmModelDescriptor
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What a [ModelCatalogRepository.refresh] did. */
data class CatalogRefresh(
    /** Models that were not in the catalog before this refresh. */
    val newModels: Int,
    /** `true` when the cache was fresh enough and nothing was fetched. */
    val skipped: Boolean,
)

/**
 * The live model catalog every app reads: the curated [LlmModelCatalog.BUILT_IN]
 * list first, then whatever the last discovery found, de-duplicated by id
 * and by download URL (a built-in wins over a discovered copy of the same
 * file) and ordered by size within the discovered part.
 *
 * Discovery results are cached in a [DiscoveredModelsStore] so the full
 * list is available offline and on the next launch. [refresh] re-runs the
 * [ModelDiscoverySource] (skipped while the cache is younger than
 * [staleAfterMillis] unless forced); a previously discovered model whose
 * file is still installed survives a refresh that no longer lists it, so
 * the user never loses access to a model they already downloaded.
 *
 * [findById] is a non-suspending snapshot for the engine's path provider
 * and other synchronous callers; it is populated with the built-ins at
 * construction and with the cache once [start] has read it. [find] waits
 * for that first read and is what background work (the download worker)
 * should use.
 */
class ModelCatalogRepository(
    private val builtIn: List<LlmModelDescriptor>,
    private val discovery: ModelDiscoverySource,
    private val store: DiscoveredModelsStore,
    private val dispatchers: DispatcherProvider,
    private val isInstalled: suspend (LlmModelDescriptor) -> Boolean = { false },
    private val staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val snapshot = AtomicReference(builtIn)
    private val loaded = CompletableDeferred<Unit>()
    private val refreshing = Mutex()

    /** Built-ins + discovered, as described on the class. */
    val models: Flow<List<LlmModelDescriptor>> = store.discovered
        .map { merge(it.models) }
        .distinctUntilChanged()

    /** Epoch millis of the last successful discovery, or `null` if none ran. */
    val lastRefreshedAt: Flow<Long?> = store.discovered.map { it.refreshedAtMillis }.distinctUntilChanged()

    /** Synchronous snapshot of [models]. */
    fun current(): List<LlmModelDescriptor> = snapshot.get()

    /** Synchronous lookup; `null` when the id is unknown (or not yet loaded from cache). */
    fun findById(id: String): LlmModelDescriptor? = current().firstOrNull { it.id == id }

    /** Lookup that waits for the cache to be read first. */
    suspend fun find(id: String): LlmModelDescriptor? {
        loaded.await()
        return findById(id)
    }

    /** Mirrors the cache into the snapshot; call once at startup (Koin single init). */
    fun start(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) {
        scope.launch {
            models.collect {
                snapshot.set(it)
                loaded.complete(Unit)
            }
        }
    }

    /**
     * Runs discovery and persists the result. Returns the failure instead
     * of throwing so callers can show "couldn't check for models" and keep
     * the cached list. Concurrent calls queue behind one another.
     */
    suspend fun refresh(force: Boolean = false): Outcome<CatalogRefresh> = refreshing.withLock {
        val cached = store.discovered.first()
        val last = cached.refreshedAtMillis
        if (!force && last != null && clock() - last < staleAfterMillis) {
            return Outcome.Success(CatalogRefresh(newModels = 0, skipped = true))
        }
        try {
            val found = withContext(dispatchers.io) { discovery.discover() }
            val foundIds = found.map { it.id }.toSet()
            val keptInstalled = cached.models.filter { it.id !in foundIds && isInstalled(it) }
            val before = current().map { it.id }.toSet()
            val merged = found + keptInstalled
            store.save(merged, clock())
            Outcome.Success(CatalogRefresh(newModels = merged.count { it.id !in before }, skipped = false))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Outcome.Failure(t)
        }
    }

    private fun merge(discovered: List<LlmModelDescriptor>): List<LlmModelDescriptor> {
        val builtInIds = builtIn.map { it.id }.toSet()
        val builtInUrls = builtIn.map { it.url }.toSet()
        val extras = discovered
            .filter { it.id !in builtInIds && it.url !in builtInUrls }
            .distinctBy { it.id }
            .sortedBy { it.sizeBytes }
        return builtIn + extras
    }

    companion object {
        /** Re-discover at most once a day unless the user asks. */
        const val DEFAULT_STALE_AFTER_MILLIS: Long = 24L * 60 * 60 * 1000
    }
}
