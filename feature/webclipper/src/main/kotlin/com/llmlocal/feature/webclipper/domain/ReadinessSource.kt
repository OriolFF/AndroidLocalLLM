package com.llmlocal.feature.webclipper.domain

import com.llmlocal.core.llm.readiness.ModelReadiness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * The one thing the clipper needs to know about the model: the shared
 * [ModelReadiness] stream. Kept as a tiny `fun interface` so tests can feed
 * states without a real observer.
 */
fun interface ReadinessSource {
    fun observe(scope: CoroutineScope): StateFlow<ModelReadiness>
}
