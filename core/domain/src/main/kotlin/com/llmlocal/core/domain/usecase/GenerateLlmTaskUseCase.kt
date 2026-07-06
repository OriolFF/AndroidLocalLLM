package com.llmlocal.core.domain.usecase

import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.repository.LlmTaskRepository
import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.llm.engine.LlmEngine
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Use case for streaming an LLM-generated result [T] from a list of inputs.
 *
 * The caller chooses the engine + task config + inputs at call time, which
 * is what enables the UI to swap between the real on-device model and a
 * fake / demo engine without re-creating the use case.
 *
 * Generic `T` is the app-specific result type (e.g. `Recipe`, `MedicalAdvice`).
 */
class GenerateLlmTaskUseCase @Inject constructor(
    private val repository: LlmTaskRepository,
) {
    operator fun <T> invoke(
        engine: LlmEngine,
        config: LlmTaskConfig<T>,
        inputs: List<String>,
    ): Flow<LlmTaskEvent<T>> = repository.generateStream(engine, config, inputs)
}