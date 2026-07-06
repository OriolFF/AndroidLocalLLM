package com.llmlocal.core.domain.repository

import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.llm.engine.LlmEngine
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level repository for generating structured tasks with the on-device
 * LLM. Implementations live in `:core:data` and wire together an
 * `LlmEngine`, a `LlmTaskConfig<T>`, and the engine's token stream.
 */
interface LlmTaskRepository {

    /**
     * Streams [LlmTaskEvent]s as the result [T] is generated, using [engine]
     * as the source of truth for the text and [config] to bind the prompt
     * and parser.
     *
     * Passing the engine in (rather than holding it in a field) lets the
     * caller swap between the real on-device engine and a fake / demo one
     * without re-creating the repository.
     *
     * The flow completes (with a [LlmTaskEvent.Complete] or
     * [LlmTaskEvent.Failed]) exactly once per call. Cancelling the
     * collector cancels the underlying generation.
     */
    fun <T> generateStream(
        engine: LlmEngine,
        config: LlmTaskConfig<T>,
        inputs: List<String>,
    ): Flow<LlmTaskEvent<T>>
}