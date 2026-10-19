package com.llmlocal.feature.webclipper.data

import com.llmlocal.core.llm.readiness.ModelReadiness
import com.llmlocal.core.llm.readiness.ModelReadinessObserver
import com.llmlocal.feature.webclipper.domain.ReadinessSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Adapts the shared `:core:llm` observer; `observe` is the only member the clipper uses. */
class ModelReadinessSource(
    private val observer: ModelReadinessObserver,
) : ReadinessSource {
    override fun observe(scope: CoroutineScope): StateFlow<ModelReadiness> = observer.observe(scope)
}
