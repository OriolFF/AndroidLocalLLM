package com.llmlocal.feature.webclipper.data

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.repository.LlmRequestOptions
import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.domain.usecase.GenerateLlmTaskUseCase
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.feature.webclipper.domain.LlmGenerationSource
import kotlinx.coroutines.flow.Flow

/** [LlmGenerationSource] over the shared [LlmEngine] and `GenerateLlmTaskUseCase`. */
class EngineLlmGenerationSource(
    private val engine: LlmEngine,
    private val generateTask: GenerateLlmTaskUseCase,
) : LlmGenerationSource {

    override suspend fun initialize(): Outcome<Unit> = engine.initialize()

    override fun <T : Any> generate(
        config: LlmTaskConfig<T>,
        input: String,
        maxOutputTokens: Int,
    ): Flow<LlmTaskEvent<T>> = generateTask(
        engine,
        config,
        listOf(input),
        LlmRequestOptions(
            systemInstruction = true,
            maxOutputTokens = maxOutputTokens,
            temperature = EXTRACTION_TEMPERATURE,
        ),
    )

    override fun cancel() = engine.cancel()

    companion object {
        /** Low temperature: extraction, not creativity. */
        const val EXTRACTION_TEMPERATURE = 0.2
    }
}
