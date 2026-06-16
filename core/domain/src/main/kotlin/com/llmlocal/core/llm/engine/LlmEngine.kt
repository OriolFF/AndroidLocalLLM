package com.llmlocal.core.llm.engine

import com.llmlocal.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the on-device LLM.
 *
 * Implementations own the lifecycle of the underlying engine and the loaded
 * model. They are expected to be **singletons** — model files are hundreds of
 * megabytes, so we must not create a new engine per ViewModel.
 *
 * The contract is:
 *  1. Call [initialize] exactly once before [generateStream].
 *  2. [generateStream] returns a hot stream of [LlmToken]s.
 *  3. The implementation is responsible for cancelling the underlying
 *     generation when the collector cancels.
 */
interface LlmEngine {
    /**
     * Loads the model into memory. Idempotent — calling twice is a no-op
     * after the first successful call. Must be called on a background
     * dispatcher; the implementation is allowed to be blocking.
     */
    suspend fun initialize(): Outcome<Unit>

    /**
     * Streams a response to [prompt] as a [Flow] of [LlmToken]s.
     *
     * @throws IllegalStateException if called before [initialize] returns
     *   a [Outcome.Success].
     */
    fun generateStream(prompt: String): Flow<LlmToken>

    /**
     * Cancels any in-flight generation. Safe to call when no generation is
     * running.
     */
    fun cancel()
}
