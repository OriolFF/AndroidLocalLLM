package com.llmlocal.feature.webclipper.fakes

import com.llmlocal.feature.webclipper.domain.StartPage
import com.llmlocal.feature.webclipper.domain.StartPageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [StartPageRepository]; [current] is the saved value. */
class FakeStartPageRepository(initial: StartPage = StartPage.DEFAULT) : StartPageRepository {
    val current = MutableStateFlow(initial)
    override val startPage: Flow<StartPage> = current
    override suspend fun set(page: StartPage) {
        current.value = page
    }
}
