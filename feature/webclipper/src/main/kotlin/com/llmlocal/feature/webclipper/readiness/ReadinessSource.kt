package com.llmlocal.feature.webclipper.readiness

import com.llmlocal.core.llm.readiness.ModelReadiness
import com.llmlocal.core.llm.readiness.ModelReadinessObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * The one thing the clipper needs to know about the model: the shared
 * [ModelReadiness] stream from `:core:llm`. Kept as a tiny `fun interface`
 * so ViewModel tests can feed states without a real observer.
 */
fun interface ReadinessSource {
    fun observe(scope: CoroutineScope): StateFlow<ModelReadiness>
}

/** Adapts the shared observer; `observe` is the only member the clipper uses. */
fun ModelReadinessObserver.asReadinessSource(): ReadinessSource =
    ReadinessSource { scope -> observe(scope) }
