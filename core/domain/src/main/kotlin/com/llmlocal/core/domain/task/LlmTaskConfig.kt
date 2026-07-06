package com.llmlocal.core.domain.task

/**
 * The seam between the generic LLM engine and an app-specific "task".
 *
 * Every white-label `:app:*` module supplies one or more
 * `LlmTaskConfig<T>` values — one per LLM-backed feature the app exposes.
 * Each config binds a [PromptBuilder] (system + user prompts) and a
 * [TaskParser] (cumulative text → result type `T`).
 *
 * ```kotlin
 * // :app:recipes
 * val recipeTaskConfig = LlmTaskConfig(
 *     promptBuilder = RecipePromptBuilder(),
 *     parser = RecipeParser(),
 * )
 * ```
 *
 * Generic `T` is the app's domain result type (e.g. `Recipe`, `MedicalAdvice`).
 * The shared `:feature:llm-task` MVI scaffold uses `T` as the result type
 * held in state.
 */
data class LlmTaskConfig<T>(
    val promptBuilder: PromptBuilder,
    val parser: TaskParser<T>,
)