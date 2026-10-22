package com.llmlocal.recipekeeper.clip.domain.detection

import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.core.web.TextSection
import com.llmlocal.feature.webclipper.domain.CandidatePayload
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.recipekeeper.clip.domain.TextCleaner
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe

/**
 * Derives detection hints from a successful extraction. Pure: no I/O.
 *
 * Emits hints with `confidence = INITIAL_CONFIDENCE` and zero counters;
 * [DetectionHintsStore.reinforce] applies the success increment.
 * Nothing is derived unless the recipe clears the quality floor
 * ([MIN_INGREDIENTS] / [MIN_STEPS]) and the candidate was a text/whole-page
 * one (the structured tier needs no help).
 */
class DeriveDetectionHintsUseCase(
    private val knownUnits: Set<String>,
    private val knownHeadings: Set<String>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    operator fun invoke(snapshot: PageSnapshot, recipe: ClippedRecipe, candidate: ClipCandidate): List<DetectionHint> {
        if (candidate.payload is CandidatePayload.Structured) return emptyList()
        val ingredients = recipe.ingredients.map(::stripBullet).filter { it.isNotBlank() }
        val steps = recipe.steps.map(::stripBullet).filter { it.isNotBlank() }
        if (ingredients.size < MIN_INGREDIENTS || steps.size < MIN_STEPS) return emptyList()

        val host = TextCleaner.host(snapshot.url).ifBlank { null }
        val now = clock()
        val out = LinkedHashMap<Triple<String?, HintKind, String>, DetectionHint>()
        fun add(hostOrNull: String?, kind: HintKind, token: String) {
            val key = Triple(hostOrNull, kind, token)
            if (token.isNotBlank() && key !in out) out[key] = DetectionHint(hostOrNull, kind, token, INITIAL_CONFIDENCE, 0, 0, now)
        }

        for (unit in learnedUnits(ingredients)) add(null, HintKind.UNIT, unit)

        val ingredientSection = bestSection(snapshot.sections, ingredients)
        val stepSection = bestSection(snapshot.sections, steps)?.takeIf { it !== ingredientSection }
        addHeading(ingredientSection, HintKind.INGREDIENT_HEADING, host, ::add)
        addHeading(stepSection, HintKind.INSTRUCTION_HEADING, host, ::add)
        if (host != null) {
            listOfNotNull(ingredientSection, stepSection)
                .filter { heading(it) != null && it.level >= MIN_LEARNED_LEVEL }
                .map { it.level }.distinct()
                .forEach { add(host, HintKind.SECTION_LEVEL, it.toString()) }
            quantityShape(ingredients)?.let { add(host, HintKind.QUANTITY_SHAPE, it.name) }
        }
        return out.values.toList()
    }

    private fun addHeading(
        section: TextSection?,
        kind: HintKind,
        host: String?,
        add: (String?, HintKind, String) -> Unit,
    ) {
        val heading = section?.let(::heading) ?: return
        if (heading in knownHeadings) return
        val single = heading.none { it.isWhitespace() }
        if (single) add(null, kind, heading) else if (host != null) add(host, kind, heading)
    }

    private fun heading(section: TextSection): String? =
        section.heading?.lowercase()?.trim()?.trimEnd(':', '.')?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_HEADING_CHARS }

    // ---- units ---------------------------------------------------------------

    /** Alphabetic tokens next to a quantity that no built-in list knows, seen on ≥ [MIN_UNIT_LINES] lines. */
    private fun learnedUnits(ingredients: List<String>): Set<String> {
        val perLine = ingredients.map { line ->
            val tokens = HashSet<String>()
            leadingUnit.find(line)?.let { tokens += it.groupValues[1] }
            trailingUnit.find(line)?.let { tokens += it.groupValues[1] }
            tokens.mapNotNull(::normaliseUnit).toSet()
        }
        return perLine.flatten().groupingBy { it }.eachCount().filterValues { it >= MIN_UNIT_LINES }.keys
    }

    private fun normaliseUnit(raw: String): String? {
        val token = raw.lowercase()
        val bare = token.trimEnd('.')
        if (bare.isEmpty() || bare.length > MAX_UNIT_CHARS || !bare.first().isLetter()) return null
        if (token in knownUnits || bare in knownUnits || "$bare." in knownUnits) return null
        if (bare in numberWords || bare in connectors) return null
        return token
    }

    // ---- quantity shape ------------------------------------------------------

    private fun quantityShape(ingredients: List<String>): QuantityShape? {
        val shapes = ingredients.map(::classify)
        val (shape, count) = shapes.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        return shape.takeIf { count * 2 > ingredients.size }
    }

    private fun classify(line: String): QuantityShape = when {
        leadingNumber.containsMatchIn(line) -> QuantityShape.NUMBER_FIRST
        numberWordFirst.containsMatchIn(line) -> QuantityShape.NUMBER_WORDS
        trailingNumber.containsMatchIn(line) -> QuantityShape.NUMBER_AFTER
        else -> QuantityShape.NO_UNITS
    }

    // ---- sections ------------------------------------------------------------

    /**
     * The headed section covering ≥ [MIN_COVERAGE] of [lines]; the most
     * specific one (shortest text) among the best-covering ones.
     */
    private fun bestSection(sections: List<TextSection>, lines: List<String>): TextSection? {
        val scored = sections.filter { heading(it) != null }.map { it to coverage(it.text, lines) }
        val best = scored.maxOfOrNull { it.second } ?: return null
        if (best < MIN_COVERAGE) return null
        return scored.filter { it.second >= best - 1e-9 }.minByOrNull { it.first.text.length }?.first
    }

    private fun coverage(sectionText: String, lines: List<String>): Double {
        val text = sectionText.lowercase().replace(whitespace, " ")
        val words = wordRegex.findAll(text).map { it.value }.toSet()
        val covered = lines.count { line ->
            val lower = line.lowercase().replace(whitespace, " ").trim()
            if (lower in text) return@count true
            val own = wordRegex.findAll(lower).map { it.value }.filter { it.length >= 3 }.toList()
            own.isNotEmpty() && own.count { it in words } * 10 >= own.size * 7
        }
        return covered.toDouble() / lines.size
    }

    private fun stripBullet(line: String): String = line.trim().trimStart('-', '*', '•', '–', '·', ' ').trim()

    private companion object {
        const val MIN_INGREDIENTS = 3
        const val MIN_STEPS = 2
        const val MIN_UNIT_LINES = 2
        const val MAX_UNIT_CHARS = 14
        const val MAX_HEADING_CHARS = 40
        const val MIN_LEARNED_LEVEL = 4
        const val MIN_COVERAGE = 0.6
        const val INITIAL_CONFIDENCE = 25

        const val NUMBER = "[\\d½¼¾⅓⅔⅛][\\d/.,\\-–½¼¾⅓⅔⅛]*"
        const val UNIT_TOKEN = "(\\p{L}[\\p{L}.]{0,13})"
        val leadingNumber = Regex("^$NUMBER(?![\\p{L}\\p{N}])|^$NUMBER\\s*\\p{L}")
        /** `200 g butter`: the token after the leading number, followed by more text (so `3 eggs` is no unit). */
        val leadingUnit = Regex("^$NUMBER\\s*$UNIT_TOKEN\\s+\\p{L}")
        /** `farina, 200 g`: the token after a trailing number. */
        val trailingUnit = Regex("\\s$NUMBER\\s*$UNIT_TOKEN\\s*$")
        val trailingNumber = Regex("\\s$NUMBER\\s*(?:\\p{L}[\\p{L}.]{0,13})?\\s*$")
        val numberWords = setOf(
            "un", "una", "uno", "dos", "tres", "dues", "one", "two", "three", "four", "five", "six", "ein", "eine", "zwei", "drei",
            "ένα", "μία", "μια", "δύο", "τρία", "τρεις",
        )
        val numberWordFirst = Regex("^(?:${numberWords.joinToString("|")})(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        val connectors = setOf("de", "of", "d", "del", "di", "van", "von", "and", "y", "i", "e", "και")
        val whitespace = Regex("\\s+")
        val wordRegex = Regex("\\p{L}+")
    }
}
