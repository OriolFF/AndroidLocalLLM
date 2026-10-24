package com.llmlocal.recipekeeper.clip.domain

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

    override fun userPrompt(inputs: List<String>): String {
        // An images clip renders its whole user turn itself (the pictures ride on the request).
        if (inputs.singleOrNull() == IMAGES_USER_TURN) return IMAGES_USER_TURN
        return buildString {
            append("Extract the recipe from this page.\n\n")
            append(inputs.joinToString("\n\n").trim())
        }
    }

    companion object {
        /**
         * Gate-decision guidance for pictures. The repository takes one system
         * prompt per config, so it travels at the head of the user turn
         * ([IMAGES_USER_TURN]) rather than as a `SYSTEM_IMAGES` system prompt.
         */
        const val IMAGES_GUIDANCE: String =
            "The pictures are consecutive screenshots or photos of one recipe; read only text that is visible; " +
                "do not guess amounts you cannot read; omit unknown fields."

        /** User turn of an images clip. */
        const val IMAGES_USER_TURN: String = "$IMAGES_GUIDANCE\n\nExtract the recipe from these pictures."

        val SYSTEM: String = """
            You extract ONE recipe from web page text. Respond ONLY in this format and nothing else:

            Title: <recipe name>
            Description: <one sentence, optional>
            Servings: <as written on the page, optional>
            PrepMinutes: <integer minutes, optional>
            CookMinutes: <integer minutes, optional>
            TotalMinutes: <integer minutes, optional>
            Ingredients:
            - <one ingredient per line, with its quantity exactly as the page writes it>
            Steps:
            1. <one step per line, in order>
            Notes:
            - <up to three short tips from the page that are not already steps, optional>

            Rules: keep ingredient lines exactly as quantities appear. Do not invent ingredients or steps that are not on the page. Copy every instruction from the page as its own step; never summarise, merge or drop steps, and never invent any. Times are whole minutes (1½ hours = 90) and only a prep, cook or total time the page explicitly states as such (e.g. "Prep time", "Cook time", "Total time"). Never infer, estimate, add up or derive a time from step durations ("bake 45 minutes" is not a cook time); if the page states no such time, omit that line entirely. If the page gives only a total, fill TotalMinutes alone and omit PrepMinutes and CookMinutes. Do not add commentary, headings or Markdown. If a field is unknown, omit that line entirely. If the page has several recipes, extract only the one the text focuses on. If the page is a list, search or category page, or otherwise contains no recipe, respond with exactly: NO RECIPE
        """.trimIndent()
    }
}
