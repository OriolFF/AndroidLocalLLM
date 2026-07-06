package com.llmlocal.core.domain.model

/**
 * Domain-level events emitted by the LLM-task generation pipeline.
 *
 * The presentation layer subscribes to a `Flow` of these and updates the
 * UI state. The events are intentionally narrow so a presenter does not
 * need to know about LLM-specific concepts (tokens, callbacks, etc.).
 *
 * `T` is the app-specific result type (e.g. `Recipe`, `MedicalAdvice`).
 */
sealed interface LlmTaskEvent<out T> {

    /**
     * A new piece of text just arrived. The full result is the *concatenation*
     * of every [Token] seen so far.
     */
    data class Token(val delta: String) : LlmTaskEvent<Nothing>

    /**
     * The model finished and we have a parsed result [T].
     */
    data class Complete<out T>(val result: T) : LlmTaskEvent<T>

    /**
     * The pipeline failed. Carries the underlying error.
     */
    data class Failed(val error: Throwable) : LlmTaskEvent<Nothing>
}