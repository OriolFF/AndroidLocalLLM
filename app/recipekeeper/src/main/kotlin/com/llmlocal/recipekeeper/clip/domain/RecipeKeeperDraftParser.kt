package com.llmlocal.recipekeeper.clip.domain

import com.llmlocal.recipekeeper.clip.domain.RecipeDraft

/**
 * Incremental parse of the streamed output for the extraction screen's
 * skeleton. Only **completed lines** (those followed by `\n`) are promoted
 * into fields; the text after the last newline is returned verbatim as
 * [RecipeDraft.tail] so the card can render it dimmed with the streaming
 * cursor. Same grammar as [RecipeKeeperParser] via [RecipeLineGrammar].
 *
 * Every prefix of a full output yields a draft whose lists only grow and
 * whose items only extend (continuation lines append to the last item).
 * Never returns `null`: an empty draft is a valid "nothing yet" state.
 */
class RecipeKeeperDraftParser {

    fun parse(streamedText: String): RecipeDraft {
        val lastNewline = streamedText.lastIndexOf('\n')
        val complete = if (lastNewline >= 0) streamedText.substring(0, lastNewline) else ""
        val tail = if (lastNewline >= 0) streamedText.substring(lastNewline + 1) else streamedText

        val parsed = if (complete.isBlank()) RecipeLineGrammar.Parsed() else RecipeLineGrammar.parse(complete.lines())
        return RecipeDraft(
            title = parsed.title,
            description = parsed.description,
            servings = parsed.servings,
            prepMinutes = parsed.prepMinutes,
            cookMinutes = parsed.cookMinutes,
            ingredients = parsed.ingredients,
            steps = parsed.steps,
            notes = parsed.notes,
            tail = tail,
        )
    }
}
