package com.llmlocal.core.domain.task

/**
 * Parses the cumulative text emitted by an `LlmEngine.generateStream(...)`
 * flow into a domain-typed result [T].
 *
 * The parser sees the **entire** text so far (every partial token has been
 * appended), and is invoked once per emitted token during streaming so the
 * UI can re-render the partial result, plus once more at `LlmToken.Done` to
 * produce the final value.
 *
 * Returns `null` when the cumulative text doesn't yet parse cleanly (still
 * streaming, or the LLM is mid-thought). Callers treat a `null` result as
 * "no result yet" — not as an error.
 *
 * Concrete implementations live in the consuming `:app:*` module (e.g.
 * `RecipeParser` in `:app:recipes`), wired into Koin via `LlmTaskConfig<T>`.
 */
fun interface TaskParser<T> {
    fun parse(cumulativeText: String): T?
}