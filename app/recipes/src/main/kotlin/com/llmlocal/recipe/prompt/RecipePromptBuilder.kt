package com.llmlocal.recipe.prompt

import com.llmlocal.core.domain.task.PromptBuilder

/**
 * Builds the system + user prompts that drive recipe generation.
 *
 * The structure is intentionally constrained so a small (1B-parameter) model
 * can produce a recipe we can parse reliably. The format mirrors what
 * [com.llmlocal.recipe.parser.RecipeParser] knows how to extract.
 */
class RecipePromptBuilder : PromptBuilder {

    override fun systemPrompt(): String = """
        You are a helpful cooking assistant. When given a list of ingredients,
        you suggest ONE simple, concrete recipe that uses mainly those
        ingredients. You respond in the following strict format and nothing
        else:

        Title: <recipe name>

        Ingredients:
        - <ingredient>
        - <ingredient>

        Steps:
        1. <step>
        2. <step>

        Notes:
        <optional short note, or omit the section>
    """.trimIndent()

    override fun userPrompt(inputs: List<String>): String {
        val list = inputs.joinToString(separator = "\n") { "- $it" }
        return """
            Here are the ingredients I have. Suggest a recipe.

            Ingredients:
            $list
        """.trimIndent()
    }
}