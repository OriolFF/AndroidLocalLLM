package com.llmlocal.core.llm.parser

import com.llmlocal.core.model.Recipe

/**
 * Parses the streamed model output into a structured [Recipe].
 *
 * The parser is **streaming-friendly**: it can be called repeatedly with
 * the cumulative text and will return the most-recent best-effort
 * interpretation. The final call is expected to contain the full model
 * output.
 *
 * The expected format (see `RecipePromptBuilder`):
 *
 * ```
 * Title: <title>
 *
 * Ingredients:
 * - a
 * - b
 *
 * Steps:
 * 1. ...
 * 2. ...
 *
 * Notes:
 * ...
 * ```
 *
 * The parser is lenient: if sections are missing or malformed, it falls
 * back to the raw text so the UI can still show *something*.
 */
class RecipeParser {

    /**
     * Parse the cumulative model output.
     *
     * @param text Full text emitted by the model so far.
     * @return A [Recipe] if the text contains a parseable structure, or
     *   `null` if not enough information is available yet. Falls back to a
     *   Recipe whose body is the raw text on partial structure.
     */
    fun parse(text: String): Recipe? {
        if (text.isBlank()) return null

        val title = extractTitle(text)
        val ingredients = extractListSection(text, "Ingredients")
        val steps = extractNumberedSection(text, "Steps")
        val notes = extractNotes(text)

        // Need at least a title and either ingredients or steps to consider
        // it a valid recipe.
        if (title == null && ingredients.isEmpty() && steps.isEmpty()) {
            return null
        }

        return Recipe(
            title = title ?: "Untitled recipe",
            ingredients = ingredients,
            steps = steps,
            notes = notes,
        )
    }

    private fun extractTitle(text: String): String? {
        // Match "Title: ..." up to end of line. The first match wins.
        val regex = Regex("(?im)^\\s*Title\\s*:\\s*(.+)$")
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractListSection(text: String, header: String): List<String> {
        // Find the section by header (case-insensitive), then collect lines
        // starting with "-" or "*" until the next blank-line-then-header or
        // end of text.
        val pattern = Regex(
            "(?ims)^\\s*${Regex.escape(header)}\\s*:?\\s*\\n(.*?)(?=^\\s*(?:Title|Ingredients|Steps|Notes)\\s*:|\\z)"
        )
        val match = pattern.find(text) ?: return emptyList()
        val body = match.groupValues[1]
        return body.lines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                    trimmed.removePrefix("-").removePrefix("*").trim().takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
    }

    private fun extractNumberedSection(text: String, header: String): List<String> {
        val pattern = Regex(
            "(?ims)^\\s*${Regex.escape(header)}\\s*:?\\s*\\n(.*?)(?=^\\s*(?:Title|Ingredients|Steps|Notes)\\s*:|\\z)"
        )
        val match = pattern.find(text) ?: return emptyList()
        val body = match.groupValues[1]
        return body.lines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                // Match leading "1." or "1)"
                val m = Regex("^\\d+[.)]\\s*(.+)").matchEntire(trimmed) ?: return@mapNotNull null
                m.groupValues[1].trim().takeIf { it.isNotEmpty() }
            }
    }

    private fun extractNotes(text: String): String? {
        val pattern = Regex(
            "(?ims)^\\s*Notes\\s*:?\\s*\\n(.*?)(?=^\\s*(?:Title|Ingredients|Steps|Notes)\\s*:|\\z)"
        )
        val match = pattern.find(text) ?: return null
        val body = match.groupValues[1].trim()
        return body.takeIf { it.isNotEmpty() }
    }
}
