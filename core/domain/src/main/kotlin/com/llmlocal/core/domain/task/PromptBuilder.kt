package com.llmlocal.core.domain.task

/**
 * Builds the two parts of an LLM prompt for a domain-agnostic "list of
 * inputs → structured output" task.
 *
 * The system prompt sets the model's role + expected output format. The
 * user prompt wraps the concrete list of inputs (already typed as
 * `List<String>` — the engine handles all stringification).
 *
 * Concrete implementations live in the consuming `:app:*` module (e.g.
 * `RecipePromptBuilder` in `:app:recipes`), wired into Koin as the value
 * half of an `LlmTaskConfig<T>`.
 *
 * Two abstract methods means this can't be a Kotlin `fun interface`; the
 * two halves (`systemPrompt` and `userPrompt`) are kept distinct on purpose
 * so the engine can concatenate them deterministically with a constant
 * separator and so callers can override one without touching the other.
 */
interface PromptBuilder {
    /** System prompt (instruction / role / output schema). */
    fun systemPrompt(): String

    /** User prompt — the list of inputs the user supplied. */
    fun userPrompt(inputs: List<String>): String
}