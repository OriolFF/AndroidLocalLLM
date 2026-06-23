package com.llmlocal.core.llm.download

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.llmlocal.core.llm.model.DownloadProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Lifecycle states for a per-model download persisted in the store.
 *  - [IDLE]      no download has ever been attempted for this id.
 *  - [RUNNING]   a worker is currently streaming bytes for this id.
 *  - [SUCCEEDED] the file is on disk (the model is installed).
 *  - [FAILED]    the last attempt failed; [failureReason] holds the detail.
 *  - [CANCELLED] the user cancelled the last attempt; not an error.
 */
enum class DownloadState { IDLE, RUNNING, SUCCEEDED, FAILED, CANCELLED }

/**
 * Snapshot of a model's download state at a point in time.
 *
 * Stored as Preferences keys per model id (prefixed). The model
 * management UI reads `Map<id, PerModelProgress>` from
 * [ModelDownloadProgressStore.progress] to render the catalog.
 */
data class PerModelProgress(
    val modelId: String,
    val state: DownloadState,
    val bytesRead: Long,
    val totalBytes: Long?,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?,
    val updatedAt: Long,
    val failureReason: String? = null,
)

/** Top-level extension: the [DataStore] for download progress snapshots. */
private val Context.modelDownloadProgressStore: DataStore<Preferences> by
    preferencesDataStore(name = "model_download_progress")

/**
 * Persists per-model download progress so the UI can re-attach to an
 * in-flight transfer after a rotation or process restart.
 *
 * The download [ModelDownloadWorker] writes via [update] / [markInstalled]
 * / [markFailed] / [markCancelled] during execution. The model-management
 * UI subscribes to [progress] to drive the catalog rows.
 */
class ModelDownloadProgressStore(private val context: Context) {

    /** Map of every model id we know about → its latest progress snapshot. */
    val progress: Flow<Map<String, PerModelProgress>> =
        context.modelDownloadProgressStore.data
            .map { prefs ->
                decodeAll(prefs)
            }
            .distinctUntilChanged()

    /** Per-id flow: emits the latest snapshot for [modelId], or `null`. */
    fun progressFor(modelId: String): Flow<PerModelProgress?> =
        context.modelDownloadProgressStore.data
            .map { prefs -> decode(prefs, modelId) }
            .distinctUntilChanged()

    /**
     * Updates the snapshot for [modelId] from a fresh [DownloadProgress]
     * event. Marks the state [DownloadState.RUNNING]. Used by the worker
     * while bytes are streaming.
     */
    suspend fun update(modelId: String, progress: DownloadProgress) {
        context.modelDownloadProgressStore.edit { prefs ->
            writeState(prefs, modelId, DownloadState.RUNNING)
            prefs[bytesReadKey(modelId)] = progress.bytesRead
            prefs[totalBytesKey(modelId)] = progress.totalBytes ?: -1L
            prefs[speedKey(modelId)] = progress.speedBytesPerSec
            prefs[etaKey(modelId)] = progress.etaSeconds ?: -1L
            prefs[updatedAtKey(modelId)] = System.currentTimeMillis()
            // RUNNING overwrites any prior FAILED/CANCELLED on a fresh start.
            prefs.remove(failureReasonKey(modelId))
        }
    }

    suspend fun markInstalled(modelId: String) {
        context.modelDownloadProgressStore.edit { prefs ->
            writeState(prefs, modelId, DownloadState.SUCCEEDED)
            val totalRaw = prefs[totalBytesKey(modelId)]
            val size = totalRaw?.takeIf { it > 0 }
            val currentBytes = prefs[bytesReadKey(modelId)] ?: 0L
            prefs[bytesReadKey(modelId)] = size ?: currentBytes
            prefs[updatedAtKey(modelId)] = System.currentTimeMillis()
            prefs.remove(failureReasonKey(modelId))
        }
    }

    suspend fun markFailed(modelId: String, reason: String) {
        context.modelDownloadProgressStore.edit { prefs ->
            writeState(prefs, modelId, DownloadState.FAILED)
            prefs[failureReasonKey(modelId)] = reason
            prefs[updatedAtKey(modelId)] = System.currentTimeMillis()
        }
    }

    suspend fun markCancelled(modelId: String) {
        context.modelDownloadProgressStore.edit { prefs ->
            writeState(prefs, modelId, DownloadState.CANCELLED)
            prefs[bytesReadKey(modelId)] = 0L
            prefs[updatedAtKey(modelId)] = System.currentTimeMillis()
            prefs.remove(failureReasonKey(modelId))
        }
    }

    /** Removes all stored progress for [modelId]. */
    suspend fun clear(modelId: String) {
        context.modelDownloadProgressStore.edit { prefs ->
            prefs.remove(stateKey(modelId))
            prefs.remove(bytesReadKey(modelId))
            prefs.remove(totalBytesKey(modelId))
            prefs.remove(speedKey(modelId))
            prefs.remove(etaKey(modelId))
            prefs.remove(updatedAtKey(modelId))
            prefs.remove(failureReasonKey(modelId))
        }
    }

    /** Snapshot read used by synchronous engine wiring paths. */
    fun snapshot(modelId: String): PerModelProgress? = runBlocking {
        context.modelDownloadProgressStore.data.map { decode(it, modelId) }.first()
    }

    // -- internal key helpers / decode --------------------------------------

    private fun stateKey(modelId: String) = stringPreferencesKey("${modelId}__state")
    private fun bytesReadKey(modelId: String) = longPreferencesKey("${modelId}__bytes")
    private fun totalBytesKey(modelId: String) = longPreferencesKey("${modelId}__total")
    private fun speedKey(modelId: String) = longPreferencesKey("${modelId}__speed")
    private fun etaKey(modelId: String) = longPreferencesKey("${modelId}__eta")
    private fun updatedAtKey(modelId: String) = longPreferencesKey("${modelId}__updated")
    private fun failureReasonKey(modelId: String) = stringPreferencesKey("${modelId}__reason")

    private fun writeState(prefs: androidx.datastore.preferences.core.MutablePreferences, modelId: String, state: DownloadState) {
        prefs[stateKey(modelId)] = state.name
    }

    private fun decode(prefs: Preferences, modelId: String): PerModelProgress? {
        val rawState = prefs[stateKey(modelId)] ?: return null
        val state = runCatching { DownloadState.valueOf(rawState) }.getOrDefault(DownloadState.IDLE)
        val bytes = prefs[bytesReadKey(modelId)] ?: 0L
        val total = prefs[totalBytesKey(modelId)]?.takeIf { it > 0 }
        val speed = prefs[speedKey(modelId)] ?: 0L
        val eta = prefs[etaKey(modelId)]?.takeIf { it >= 0 }
        val updated = prefs[updatedAtKey(modelId)] ?: 0L
        val reason = prefs[failureReasonKey(modelId)]
        return PerModelProgress(
            modelId = modelId,
            state = state,
            bytesRead = bytes,
            totalBytes = total,
            speedBytesPerSec = speed,
            etaSeconds = eta,
            updatedAt = updated,
            failureReason = reason,
        )
    }

    /**
     * Decodes snapshots for every model id we have any state for. Models
     * never seen are simply absent from the returned map.
     */
    private fun decodeAll(prefs: Preferences): Map<String, PerModelProgress> {
        val ids = prefs.asMap().keys
            .mapNotNull { key ->
                val name = key.name
                val suffix = "__state"
                if (name.endsWith(suffix)) name.removeSuffix(suffix) else null
            }
            .distinct()
        return ids.associateWith { id -> decode(prefs, id)!! }
    }
}