package com.llmlocal.core.model

/**
 * A single ingredient the user wants the recipe to use.
 *
 * The name is intentionally a plain String: the LLM is responsible for
 * interpreting free-form input ("a few ripe tomatoes", "1 cup of flour")
 * and the domain layer should not constrain it.
 */
data class Ingredient(
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "Ingredient name must not be blank" }
    }
}
