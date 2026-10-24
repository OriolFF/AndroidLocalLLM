package com.llmlocal.feature.webclipper.domain

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.llm.engine.LlmCapabilities
import com.llmlocal.core.llm.engine.LlmImage
import kotlinx.coroutines.flow.Flow

/**
 * The on-device model as the clip pipeline sees it: warm it up, stream one
 * extraction, stop it. The `data/` adapter owns the engine, the system
 * instruction, the sampling temperature and `GenerateLlmTaskUseCase`.
 */
interface LlmGenerationSource {

    /** Loads the model if needed; idempotent. */
    suspend fun initialize(): Outcome<Unit>

    /**
     * Runs [config] over the single rendered [input], emitting raw deltas
     * and one terminal `Complete` / `Failed`, capped at [maxOutputTokens].
     * [images] travel on the request before the text, at [visualTokenBudget]
     * tokens each (`null` = runtime default).
     */
    fun <T : Any> generate(
        config: LlmTaskConfig<T>,
        input: String,
        maxOutputTokens: Int,
        images: List<LlmImage> = emptyList(),
        visualTokenBudget: Int? = null,
    ): Flow<LlmTaskEvent<T>>

    /** What the loaded engine can take; text only by default. */
    fun capabilities(): LlmCapabilities = LlmCapabilities()

    /** Stops native compute for the generation in flight, if any. */
    fun cancel()
}
