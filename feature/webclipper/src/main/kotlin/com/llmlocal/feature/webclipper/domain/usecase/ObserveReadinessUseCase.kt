package com.llmlocal.feature.webclipper.domain.usecase

import com.llmlocal.core.llm.readiness.ModelReadiness
import com.llmlocal.feature.webclipper.domain.ReadinessSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** The shared model-readiness stream, shared in [scope]. */
class ObserveReadinessUseCase(private val source: ReadinessSource) {
    operator fun invoke(scope: CoroutineScope): StateFlow<ModelReadiness> = source.observe(scope)
}
