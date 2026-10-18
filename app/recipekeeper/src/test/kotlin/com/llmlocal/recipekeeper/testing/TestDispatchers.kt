package com.llmlocal.recipekeeper.testing

import com.llmlocal.core.common.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/** [DispatcherProvider] that routes every lane to one test dispatcher. */
class TestDispatchers(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}
