package com.llmlocal.core.domain.model

import com.llmlocal.core.model.Recipe

/**
 * Domain-level events emitted by the recipe generation pipeline.
 *
 * The presentation layer subscribes to a [Flow] of these and updates the
 * UI state. The events are intentionally narrow so a presenter does not
 * need to know about LLM-specific concepts (tokens, callbacks, etc.).
 */
sealed interface RecipeEvent {

    /**
     * A new piece of text just arrived. The full recipe is the *concatenation*
     * of every [Token] seen so far.
     */
    data class Token(val delta: String) : RecipeEvent

    /**
     * The model finished and we have a parsed [Recipe].
     */
    data class Complete(val recipe: Recipe) : RecipeEvent

    /**
     * The pipeline failed. Carries the underlying error.
     */
    data class Failed(val error: Throwable) : RecipeEvent
}
