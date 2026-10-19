package com.llmlocal.recipekeeper.clip.domain

import com.llmlocal.recipekeeper.clip.domain.RecipeDraft

/**
 * Minimal incremental parser for the v1 line format (plan §3.4) so the
 * draft card and its previews work before WS5a's `RecipeKeeperDraftParser`
 * is wired in at merge. Only **complete** lines (terminated by `\n`) are
 * promoted into fields; the unterminated last line is returned as
 * [RecipeDraft.tail] so the card can render it dimmed behind the cursor.
 *
 * Recognised headers: `Title:`, `Description:`, `Servings:`, `PrepMinutes:`,
 * `CookMinutes:`, `Ingredients:` (then `-`/`*`/`•` bullets), `Steps:`
 * (then `1.` / `1)` / `Step 1` lines), `Notes:` (free text).
 */
object StubDraftParser {

    private enum class Section { NONE, INGREDIENTS, STEPS, NOTES }

    private val bullet = Regex("""^\s*[-*•]\s*(.+)$""")
    private val numbered = Regex("""^\s*(?:step\s*)?\d+\s*[.):]\s*(.+)$""", RegexOption.IGNORE_CASE)

    fun parse(text: String): RecipeDraft {
        val lines = text.split('\n')
        val complete = lines.dropLast(1)
        val tail = lines.last()

        var title: String? = null
        var description: String? = null
        var servings: String? = null
        var prep: Int? = null
        var cook: Int? = null
        val ingredients = mutableListOf<String>()
        val steps = mutableListOf<String>()
        val notes = StringBuilder()
        var section = Section.NONE

        for (raw in complete) {
            val line = raw.trim()
            val header = line.substringBefore(':', missingDelimiterValue = "").trim().lowercase()
            val value = line.substringAfter(':', missingDelimiterValue = "").trim()
            when (header) {
                "title" -> { title = value.ifBlank { null }; section = Section.NONE; continue }
                "description" -> { description = value.ifBlank { null }; section = Section.NONE; continue }
                "servings" -> { servings = value.ifBlank { null }; section = Section.NONE; continue }
                "prepminutes" -> { prep = value.toIntOrNull(); section = Section.NONE; continue }
                "cookminutes" -> { cook = value.toIntOrNull(); section = Section.NONE; continue }
                "ingredients" -> { section = Section.INGREDIENTS; continue }
                "steps" -> { section = Section.STEPS; continue }
                "notes" -> { section = Section.NOTES; continue }
            }
            when (section) {
                Section.INGREDIENTS -> bullet.find(line)?.groupValues?.get(1)?.trim()?.let(ingredients::add)
                Section.STEPS -> numbered.find(line)?.groupValues?.get(1)?.trim()?.let(steps::add)
                Section.NOTES -> if (line.isNotBlank()) {
                    if (notes.isNotEmpty()) notes.append('\n')
                    notes.append(line)
                }
                Section.NONE -> Unit
            }
        }
        return RecipeDraft(
            title = title,
            description = description,
            servings = servings,
            prepMinutes = prep,
            cookMinutes = cook,
            ingredients = ingredients,
            steps = steps,
            notes = notes.toString().ifBlank { null },
            tail = tail,
        )
    }
}
