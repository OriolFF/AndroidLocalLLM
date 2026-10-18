package com.llmlocal.core.domain.usecase

import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.repository.LlmRequestOptions
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
 * Generic `T` is the app-specific result type (e.g. `Recipe`,
 * `MedicalAdvice`, `ClippedRecipe`).
 */
class GenerateLlmTaskUseCase @Inject constructor(
    private val repository: LlmTaskRepository,
) {
    /**
     * Legacy 3-arg overload. Calls the repository with
     * [LlmRequestOptions.Legacy] (system + user concatenated into one
     * prompt, no tuning) — used by `:app:recipes`, `:app:medical`,
     * `:feature:llm-task`.
     */
    operator fun <T> invoke(
        engine: LlmEngine,
        config: LlmTaskConfig<T>,
        inputs: List<String>,
    ): Flow<LlmTaskEvent<T>> = repository.generateStream(engine, config, inputs)

    /**
     * Overload with `LlmRequest` extras. Use when the prompt builder
     * produces a stable system instruction and the caller wants the engine
     * to keep it separate from the user turn (the clipper does this so it
     * can pass `maxOutputTokens` + `temperature` independently).
     */
    operator fun <T> invoke(
        engine: LlmEngine,
        config: LlmTaskConfig<T>,
        inputs: List<String>,
        options: LlmRequestOptions,
    ): Flow<LlmTaskEvent<T>> = repository.generateStream(engine, config, inputs, options)
}
