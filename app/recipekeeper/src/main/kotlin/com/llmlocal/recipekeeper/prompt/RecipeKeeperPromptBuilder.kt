package com.llmlocal.recipekeeper.prompt

import com.llmlocal.core.domain.task.PromptBuilder

/**
 * System + user prompts for extracting ONE recipe from rendered page text
 * (plan §3.4). The format is the line grammar `RecipeKeeperParser`
 * understands; change one, change the other.
 *
 * [userPrompt] receives exactly one input: the output of
 * `RecipeKeeperClipConfig.renderLlmInput`, which already starts with a
 * `Page: <host> — <title>` line. Sampling temperature (0.2) and the output
 * cap are request options the extraction ViewModel sets on `LlmRequest`;
 * they are not part of the prompt text.
 */
class RecipeKeeperPromptBuilder : PromptBuilder {

    override fun systemPrompt(): String = SYSTEM

    override fun userPrompt(inputs: List<String>): String = buildString {
        append("Extract the recipe from this page.\n\n")
        append(inputs.joinToString("\n\n").trim())
    }

    companion object {
        val SYSTEM: String = """
            You extract ONE recipe from web page text. Respond ONLY in this format and nothing else:

            Title: <recipe name>
            Description: <one sentence, optional>
            Servings: <as written on the page, optional>
            PrepMinutes: <integer minutes, optional>
            CookMinutes: <integer minutes, optional>
            Ingredients:
            - <one ingredient per line, with its quantity exactly as the page writes it>
            Steps:
            1. <one step per line, in order>
            Notes:
            <short tips from the page, optional>

            Rules: keep ingredient lines exactly as quantities appear. Do not invent ingredients or steps that are not on the page. Do not add commentary, headings or Markdown. If a field is unknown, omit that line entirely. If the page has several recipes, extract only the one the text focuses on.
        """.trimIndent()
    }
}
