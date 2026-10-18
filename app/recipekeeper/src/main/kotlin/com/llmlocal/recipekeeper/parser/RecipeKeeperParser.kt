package com.llmlocal.recipekeeper.parser

import com.llmlocal.core.domain.task.TaskParser
import com.llmlocal.recipekeeper.domain.ClippedRecipe

/**
 * Final parse of the model output into a [ClippedRecipe]
 * ([RecipeLineGrammar] for the grammar and tolerances).
 *
 * Returns `null` when the model refused with `NO RECIPE` (the prompt's
 * answer for pages without a recipe) or when the text has **neither
 * ingredients nor steps**; the extraction ViewModel then tries
 * `RecipeKeeperClipConfig.fallback` for STRUCTURED candidates or shows the
 * parse-failed state.
 *
 * The result is *detached*: the parser does not know the page, so
 * [ClippedRecipe.id], [ClippedRecipe.sourceUrl], [ClippedRecipe.sourceHost]
 * are empty strings, [ClippedRecipe.pictures] is empty and
 * [ClippedRecipe.title] is `""` when the model omitted it.
 * `RecipeKeeperClipSink.save` fills all of these from the snapshot and
 * candidate before persisting.
 *
 * [ClippedRecipe.truncated] is a heuristic: `true` when the output ends
 * inside the Steps section on a line without terminal punctuation. The
 * ViewModel combines it with its own token-cap check.
 */
class RecipeKeeperParser : TaskParser<ClippedRecipe> {

    override fun parse(cumulativeText: String): ClippedRecipe? {
        if (cumulativeText.isBlank()) return null
        if (isRefusal(cumulativeText)) return null
        val parsed = RecipeLineGrammar.parse(cumulativeText.lines())
        if (parsed.ingredients.isEmpty() && parsed.steps.isEmpty()) return null

        val prep = parsed.prepMinutes
        val cook = parsed.cookMinutes
        // An explicit TotalMinutes (the page's own total) beats a prep + cook sum.
        val total = parsed.totalMinutes ?: if (prep != null || cook != null) (prep ?: 0) + (cook ?: 0) else null

        return ClippedRecipe(
            id = "",
            title = parsed.title.orEmpty(),
            description = parsed.description,
            servings = parsed.servings,
            prepMinutes = prep,
            cookMinutes = cook,
            totalMinutes = total,
            ingredients = parsed.ingredients,
            steps = parsed.steps,
            notes = parsed.notes,
            sourceUrl = "",
            sourceHost = "",
            pictures = emptyList(),
            truncated = looksTruncated(cumulativeText, parsed),
            clippedAt = 0L,
        )
    }

    /** The first non-blank line is `NO RECIPE` (any case, optional decoration or full stop). */
    private fun isRefusal(text: String): Boolean {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        return NO_RECIPE.matches(first)
    }

    private fun looksTruncated(text: String, parsed: RecipeLineGrammar.Parsed): Boolean {
        if (parsed.lastSection != RecipeLineGrammar.Section.STEPS) return false
        val last = parsed.lastLine.trim()
        if (last.isEmpty() || RecipeLineGrammar.isHeader(last)) return false
        // A step that ends with a sentence terminator (optionally followed by
        // a closing quote/bracket) is complete; anything else mid-sentence is
        // suspicious. Output ending with a newline after punctuation is fine.
        val terminal = Regex("[.!?…。]['\")\\]]*$")
        return !terminal.containsMatchIn(last) && !text.endsWith("\n\n")
    }

    companion object {
        /** Exact refusal text the prompt asks for. */
        const val NO_RECIPE_TEXT = "NO RECIPE"

        private val NO_RECIPE = Regex("^\\s*[#*_`\"']*no\\s+recipe[*_`\"'.!]*\\s*$", RegexOption.IGNORE_CASE)
    }
}
