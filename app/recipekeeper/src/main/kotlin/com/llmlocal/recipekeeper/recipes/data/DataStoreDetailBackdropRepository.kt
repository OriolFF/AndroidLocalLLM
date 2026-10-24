package com.llmlocal.recipekeeper.recipes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.llmlocal.recipekeeper.recipes.domain.DetailBackdrop
import com.llmlocal.recipekeeper.recipes.domain.DetailBackdropRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Top-level extension: the [DataStore] holding the app's appearance settings. */
private val Context.appearanceSettingsStore: DataStore<Preferences> by
    preferencesDataStore(name = "appearance_settings")

/**
 * [DetailBackdropRepository] on Preferences DataStore (`appearance_settings`,
 * key `detail_backdrop` = the enum name). Missing or unknown values read as
 * [DetailBackdrop.DEFAULT]. Registered as a Koin `single`.
 */
class DataStoreDetailBackdropRepository(private val context: Context) : DetailBackdropRepository {

    override val backdrop: Flow<DetailBackdrop> = context.appearanceSettingsStore.data
        .map { prefs -> DetailBackdrop.fromStorageName(prefs[BACKDROP_KEY]) }
        .distinctUntilChanged()

    override suspend fun set(backdrop: DetailBackdrop) {
        context.appearanceSettingsStore.edit { prefs -> prefs[BACKDROP_KEY] = backdrop.name }
    }

    private companion object {
        val BACKDROP_KEY = stringPreferencesKey("detail_backdrop")
    }
}
