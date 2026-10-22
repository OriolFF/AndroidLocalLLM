package com.llmlocal.feature.webclipper.domain

import kotlinx.coroutines.flow.Flow

/** Persists the browser's [StartPage]; implemented in `data` on Preferences DataStore. */
interface StartPageRepository {
    /** Hot flow of the saved start page; emits [StartPage.DEFAULT] when nothing was saved. */
    val startPage: Flow<StartPage>

    suspend fun set(page: StartPage)
}
