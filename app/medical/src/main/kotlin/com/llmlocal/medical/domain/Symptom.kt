package com.llmlocal.medical.domain

/**
 * A single symptom the user wants advice on.
 *
 * Like [com.llmlocal.recipe.domain.Ingredient], this is a thin wrapper
 * around a `String` so the same `LlmTaskConfig<String>` plumbing works
 * — the domain type just enforces non-blank input at the type level.
 */
data class Symptom(
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "Symptom name must not be blank" }
    }
}