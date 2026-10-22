package com.llmlocal.feature.webclipper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.llmlocal.feature.webclipper.domain.StartPage
import com.llmlocal.feature.webclipper.domain.StartPageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Top-level extension: the [DataStore] holding the browser's settings. */
private val Context.browserSettingsStore: DataStore<Preferences> by
    preferencesDataStore(name = "browser_settings")

/**
 * [StartPageRepository] on Preferences DataStore (`browser_settings`): a
 * `start_page_kind` of `google` / `blank` / `custom` plus `start_page_url`
 * for the custom kind. Anything missing or unreadable falls back to
 * [StartPage.DEFAULT]. Registered as a Koin `single`.
 */
class DataStoreStartPageRepository(private val context: Context) : StartPageRepository {

    override val startPage: Flow<StartPage> = context.browserSettingsStore.data
        .map { prefs ->
            when (prefs[KIND_KEY]) {
                KIND_BLANK -> StartPage.Blank
                KIND_CUSTOM -> prefs[URL_KEY]?.takeIf { it.isNotBlank() }?.let { StartPage.Custom(it) }
                    ?: StartPage.DEFAULT
                KIND_GOOGLE -> StartPage.Google
                else -> StartPage.DEFAULT
            }
        }
        .distinctUntilChanged()

    override suspend fun set(page: StartPage) {
        context.browserSettingsStore.edit { prefs ->
            when (page) {
                StartPage.Google -> {
                    prefs[KIND_KEY] = KIND_GOOGLE
                    prefs.remove(URL_KEY)
                }
                StartPage.Blank -> {
                    prefs[KIND_KEY] = KIND_BLANK
                    prefs.remove(URL_KEY)
                }
                is StartPage.Custom -> {
                    prefs[KIND_KEY] = KIND_CUSTOM
                    prefs[URL_KEY] = page.url
                }
            }
        }
    }

    private companion object {
        val KIND_KEY = stringPreferencesKey("start_page_kind")
        val URL_KEY = stringPreferencesKey("start_page_url")
        const val KIND_GOOGLE = "google"
        const val KIND_BLANK = "blank"
        const val KIND_CUSTOM = "custom"
    }
}
