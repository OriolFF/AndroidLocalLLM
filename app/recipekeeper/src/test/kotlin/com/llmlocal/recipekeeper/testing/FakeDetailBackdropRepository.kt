package com.llmlocal.recipekeeper.testing

import com.llmlocal.recipekeeper.recipes.domain.DetailBackdrop
import com.llmlocal.recipekeeper.recipes.domain.DetailBackdropRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [DetailBackdropRepository]; [current] is the saved value. */
class FakeDetailBackdropRepository(initial: DetailBackdrop = DetailBackdrop.DEFAULT) : DetailBackdropRepository {
    val current = MutableStateFlow(initial)
    override val backdrop: Flow<DetailBackdrop> = current
    override suspend fun set(backdrop: DetailBackdrop) {
        current.value = backdrop
    }
}
