package com.llmlocal.core.llm.engine

/**
 * A single piece of the streamed LLM response.
 *
 * - [Partial] is a chunk of text the model just emitted.
 * - [Done] signals the end of the response.
 * - [Error] carries an exception that aborted the response.
 */
sealed interface LlmToken {
    data class Partial(val text: String) : LlmToken
    data object Done : LlmToken
    data class Error(val error: Throwable) : LlmToken
}
