package com.llmlocal.feature.llmtask.domain.usecase

import com.llmlocal.feature.llmtask.domain.ModelReadinessSource

/** Re-runs the model disk + engine check (e.g. a "Retry" action). */
class RecheckModelUseCase(private val source: ModelReadinessSource) {
    suspend operator fun invoke() = source.recheck()
}
