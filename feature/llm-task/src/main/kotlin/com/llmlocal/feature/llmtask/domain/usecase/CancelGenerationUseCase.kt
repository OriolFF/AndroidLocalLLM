package com.llmlocal.feature.llmtask.domain.usecase

import com.llmlocal.feature.llmtask.domain.LlmGenerationSource

/** Stops the in-flight generation. */
class CancelGenerationUseCase(private val source: LlmGenerationSource) {
    operator fun invoke() = source.cancel()
}
