package com.llmlocal.core.llm.selection

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.llmlocal.core.llm.model.LlmModelCatalog
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Top-level extension: the [DataStore] for the "currently selected model" id. */
private val Context.modelSelectionStore: DataStore<Preferences> by
    preferencesDataStore(name = "model_selection")

/**
 * Persists the user's "currently selected model" choice across app
 * restarts, and exposes a non-suspending [current] snapshot so the
 * `LiteRtLlmEngine.modelPathProvider` lambda (which is **not** `suspend`)
 * can resolve the file path without blocking.
 *
 * The snapshot is backed by an [AtomicReference] that's kept in sync by a
 * long-running collector launched in [start]. We accept the small risk of
 * the snapshot being slightly stale across processes (a few ms) because
 * the only consumer is `LiteRtLlmEngine.initialize`, which is itself
 * idempotent and re-reads the store on every call.
 *
 * If no model has ever been selected, [current] falls back to
 * [LlmModelCatalog.DEFAULT_MODEL.id] so the engine always has a sensible
 * default — the user just needs to make sure the corresponding file is
 * installed (the engine already validates this).
 */
class LlmModelSelectionStore(
    private val context: Context,
) {

    private val currentRef = AtomicReference<String?>(null)

    /**
     * Hot flow of the selected model id. Emits `null` when the user has
     * never picked a model, and [LlmModelCatalog.DEFAULT_MODEL.id] when
     * no explicit choice has been persisted.
     */
    val selectedModelId: Flow<String?> = context.modelSelectionStore.data
        .map { prefs ->
            prefs[SELECTED_MODEL_ID_KEY] ?: LlmModelCatalog.DEFAULT_MODEL.id
        }
        .distinctUntilChanged()

    /**
     * Synchronous snapshot of the latest known selection. Returns
     * [LlmModelCatalog.DEFAULT_MODEL.id] when nothing is set, so callers
     * (e.g. the engine) always get a non-null catalog id.
     */
    fun current(): String = currentRef.get() ?: LlmModelCatalog.DEFAULT_MODEL.id

    /** Stores [modelId] as the user's selected model. */
    suspend fun setSelected(modelId: String) {
        context.modelSelectionStore.edit { prefs ->
            prefs[SELECTED_MODEL_ID_KEY] = modelId
        }
    }

    /** Clears the user's selection. Subsequent [current] falls back to the default. */
    suspend fun clear() {
        context.modelSelectionStore.edit { prefs ->
            prefs.remove(SELECTED_MODEL_ID_KEY)
        }
    }

    /**
     * Starts a long-running collector that mirrors the persisted value
     * into the in-memory [currentRef] snapshot. Must be called once at
     * app startup (Koin `single { ... }` init in [com.llmlocal.core.llm.di.llmModule]).
     *
     * The collector runs on [Dispatchers.Default] and is bound to [scope];
     * callers can pass an application-scoped scope.
     */
    fun start(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) {
        scope.launch {
            selectedModelId.collect { currentRef.set(it) }
        }
    }

    companion object {
        private val SELECTED_MODEL_ID_KEY = stringPreferencesKey("selected_model_id")
    }
}