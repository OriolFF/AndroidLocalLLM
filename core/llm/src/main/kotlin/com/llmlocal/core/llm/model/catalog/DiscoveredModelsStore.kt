package com.llmlocal.core.llm.model.catalog

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.llmlocal.core.model.LlmModelDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Persisted result of the last model discovery plus when it ran. */
data class DiscoveredModels(
    val models: List<LlmModelDescriptor>,
    val refreshedAtMillis: Long?,
)

/** Cache of discovered models; [DataStoreDiscoveredModelsStore] is the production implementation. */
interface DiscoveredModelsStore {
    val discovered: Flow<DiscoveredModels>

    suspend fun save(models: List<LlmModelDescriptor>, refreshedAtMillis: Long)
}

private val Context.modelCatalogStore: DataStore<Preferences> by
    preferencesDataStore(name = "model_catalog")

/**
 * Preferences DataStore (`model_catalog`) holding the discovered descriptors
 * as one JSON document plus the refresh timestamp. A corrupt document, or
 * one written by an older [CACHE_VERSION] (the mapper's filtering and
 * naming changed), reads as "nothing discovered, never refreshed" so the
 * next screen open re-runs discovery; the built-ins are unaffected.
 */
class DataStoreDiscoveredModelsStore(private val context: Context) : DiscoveredModelsStore {

    override val discovered: Flow<DiscoveredModels> = context.modelCatalogStore.data
        .map { prefs ->
            if (prefs[VERSION] != CACHE_VERSION) return@map DiscoveredModels(emptyList(), null)
            val models = prefs[MODELS_JSON]?.let { json -> runCatching { DescriptorJson.decode(json) }.getOrNull() }
            DiscoveredModels(models = models.orEmpty(), refreshedAtMillis = prefs[REFRESHED_AT])
        }
        .distinctUntilChanged()

    override suspend fun save(models: List<LlmModelDescriptor>, refreshedAtMillis: Long) {
        context.modelCatalogStore.edit { prefs ->
            prefs[MODELS_JSON] = DescriptorJson.encode(models)
            prefs[REFRESHED_AT] = refreshedAtMillis
            prefs[VERSION] = CACHE_VERSION
        }
    }

    companion object {
        /** Bump when the mapper's filtering or naming changes so stale caches are discarded. */
        const val CACHE_VERSION = 2
        private val MODELS_JSON = stringPreferencesKey("discovered_models_json")
        private val REFRESHED_AT = longPreferencesKey("discovered_refreshed_at")
        private val VERSION = androidx.datastore.preferences.core.intPreferencesKey("discovered_cache_version")
    }
}
