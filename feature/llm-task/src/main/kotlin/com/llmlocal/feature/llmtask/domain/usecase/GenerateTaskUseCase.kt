package com.llmlocal.feature.llmtask.domain.usecase

import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.feature.llmtask.domain.LlmGenerationSource
import kotlinx.coroutines.flow.Flow

/**
 * Streams the result of [config] for [inputs]. Engine initialisation is part
 * of the stream, so callers never touch the engine.
 */
class GenerateTaskUseCase(private val source: LlmGenerationSource) {
    operator fun <T> invoke(config: LlmTaskConfig<T>, inputs: List<String>): Flow<LlmTaskEvent<T>> =
        source.generate(config, inputs)
}
