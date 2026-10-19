package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.EditSection

/** Splits pasted multi-line text into editor rows, stripping bullets and numbering. */
class SplitPastedLinesUseCase {

    operator fun invoke(text: String, section: EditSection): List<String> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n')
        return when (section) {
            EditSection.INGREDIENTS -> lines.split('\n')
                .map { it.trim().replace(INGREDIENT_PREFIX, "").trim() }
                .filter { it.isNotEmpty() }
            EditSection.STEPS -> splitSteps(lines)
        }
    }

    private fun splitSteps(text: String): List<String> {
        val lines = text.split('\n')
        val markerCount = lines.count { STEP_MARKER.containsMatchIn(it.trim()) }
        val parts = when {
            markerCount >= 2 -> {
                val steps = mutableListOf<StringBuilder>()
                for (raw in lines) {
                    val line = raw.trim()
                    if (STEP_MARKER.containsMatchIn(line)) {
                        steps += StringBuilder(line.replace(STEP_MARKER, "").trim())
                    } else if (steps.isNotEmpty() && line.isNotEmpty()) {
                        steps.last().append('\n').append(line)
                    } else if (line.isNotEmpty()) {
                        steps += StringBuilder(line)
                    }
                }
                steps.map { it.toString() }
            }
            BLANK_LINE.containsMatchIn(text.trim()) -> text.trim().split(BLANK_LINE)
            else -> listOf(text)
        }
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private companion object {
        val INGREDIENT_PREFIX = Regex("""^(?:[-*•·]\s+|\(?\d+[.)]\s+)""")
        val STEP_MARKER = Regex("""^(?:\(?\d+[.)]|step\s*\d+[:.]?|[①-⑳])\s*""", RegexOption.IGNORE_CASE)
        val BLANK_LINE = Regex("""\n\s*\n""")
    }
}
