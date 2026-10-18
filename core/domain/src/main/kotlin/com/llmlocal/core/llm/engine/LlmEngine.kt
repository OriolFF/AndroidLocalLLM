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
 *  2. [generateStream] returns a cold stream of [LlmToken]s; one call is one
 *     generation. Only one generation runs at a time — a second call waits
 *     for the first to finish or be cancelled.
 *  3. Cancelling the collecting coroutine stops the underlying generation;
 *     the implementation is responsible for releasing native resources and
 *     must not surface the cancellation as an [LlmToken.Error].
 *  4. [cancel] additionally stops the in-flight generation from outside the
 *     collector (e.g. a Stop button whose job was already cancelled).
 */
interface LlmEngine {
    /**
     * Loads the model into memory. Idempotent — calling twice is a no-op
     * after the first successful call. Must be called on a background
     * dispatcher; the implementation is allowed to be blocking.
     */
    suspend fun initialize(): Outcome<Unit>

    /**
     * Streams a response to [request] as a [Flow] of [LlmToken]s.
     *
     * The flow emits zero or more [LlmToken.Partial]s followed by exactly one
     * terminal token: [LlmToken.Done] on success or [LlmToken.Error] on
     * failure. If the generation is stopped through [cancel] while the
     * collector is still alive the flow completes without a terminal token.
     *
     * Emits [LlmToken.Error] (rather than throwing) if called before
     * [initialize] returned an [Outcome.Success].
     */
    fun generateStream(request: LlmRequest): Flow<LlmToken>

    /**
     * Convenience overload: streams a response to a bare [prompt] with every
     * other [LlmRequest] field left at its default. Equivalent to
     * `generateStream(LlmRequest(prompt))`; implementations should override
     * the [LlmRequest] variant only.
     */
    fun generateStream(prompt: String): Flow<LlmToken> = generateStream(LlmRequest(prompt))

    /**
     * Cancels any in-flight generation. Safe to call when no generation is
     * running.
     */
    fun cancel()
}
