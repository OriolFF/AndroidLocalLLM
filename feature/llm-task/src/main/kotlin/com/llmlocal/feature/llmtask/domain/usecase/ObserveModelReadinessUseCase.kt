package com.llmlocal.feature.llmtask.domain.usecase

import com.llmlocal.feature.llmtask.domain.ModelAvailability
import com.llmlocal.feature.llmtask.domain.ModelReadinessSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Observes whether the selected model is ready to run. */
class ObserveModelReadinessUseCase(private val source: ModelReadinessSource) {
    operator fun invoke(scope: CoroutineScope): StateFlow<ModelAvailability> = source.observe(scope)
}
