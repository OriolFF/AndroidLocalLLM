package com.llmlocal.core.model

/**
 * A recipe returned by the LLM after parsing the streamed text.
 *
 * @property title The recipe name.
 * @property ingredients The ingredients the model decided to use (typically a
 *   subset of the user-supplied ingredients, with optional additions such as
 *   "salt" or "olive oil" that the model considers necessary).
 * @property steps Ordered cooking instructions. The first step is step 1.
 * @property notes Optional free-form notes (e.g. "Best served hot").
 */
data class Recipe(
    val title: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val notes: String? = null,
)
