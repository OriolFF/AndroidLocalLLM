package com.llmlocal.feature.webclipper.domain

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.task.LlmTaskConfig
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
     */
    fun <T : Any> generate(config: LlmTaskConfig<T>, input: String, maxOutputTokens: Int): Flow<LlmTaskEvent<T>>

    /** Stops native compute for the generation in flight, if any. */
    fun cancel()
}
