package com.llmlocal.recipekeeper.parser

/**
 * The line-oriented output grammar shared by [RecipeKeeperParser] (final
 * parse at `Done`) and [RecipeKeeperDraftParser] (incremental parse while
 * streaming). One state machine over lines so both see the same thing.
 *
 * Grammar (see `RecipeKeeperPromptBuilder` for what the model is asked):
 *
 * ```
 * Title: …
 * Description: …
 * Servings: …
 * PrepMinutes: <int>
 * CookMinutes: <int>
 * Ingredients:
 * - …
 * Steps:
 * 1. …
 * Notes:
 * …
 * ```
 *
 * Tolerances: header synonyms in several languages, optional Markdown
 * `#`/`**` decoration, bullets `-`, `*`, `•`, `–`, steps as `1.`, `1)`,
 * `Step 1:` or bullets, continuation lines appended to the previous item,
 * `PrepMinutes: 1 h 15 min` style durations, a `TotalMinutes:` header the
 * prompt does not ask for but models sometimes add.
 */
internal object RecipeLineGrammar {

    enum class Section { NONE, TITLE, DESCRIPTION, SERVINGS, PREP, COOK, TOTAL, INGREDIENTS, STEPS, NOTES }

    /** Everything the state machine extracted; `lastSection` drives truncation heuristics. */
    data class Parsed(
        val title: String? = null,
        val description: String? = null,
        val servings: String? = null,
        val prepMinutes: Int? = null,
        val cookMinutes: Int? = null,
        val totalMinutes: Int? = null,
        val ingredients: List<String> = emptyList(),
        val steps: List<String> = emptyList(),
        val notes: String? = null,
        val lastSection: Section = Section.NONE,
        val lastLine: String = "",
    )

    private val headerSynonyms: List<Pair<Section, Regex>> = listOf(
        Section.TITLE to words("Title", "Name", "Recipe", "Recipe name", "Recipe title", "Titre", "Título", "Titulo", "Titel", "Titolo", "Títol"),
        Section.DESCRIPTION to words("Description", "Summary", "Descripción", "Descripcion", "Beschreibung", "Descrizione", "Descripció"),
        Section.SERVINGS to words("Servings", "Serves", "Yield", "Yields", "Portions", "Porciones", "Raciones", "Portionen", "Porzioni", "Racions"),
        Section.PREP to words("PrepMinutes", "Prep Minutes", "Prep time", "Preparation time", "Prep", "Tiempo de preparación", "Vorbereitungszeit", "Temps de préparation"),
        Section.COOK to words("CookMinutes", "Cook Minutes", "Cook time", "Cooking time", "Cook", "Tiempo de cocción", "Kochzeit", "Temps de cuisson"),
        Section.TOTAL to words("TotalMinutes", "Total Minutes", "Total time", "Total", "Tiempo total", "Gesamtzeit", "Temps total"),
        Section.INGREDIENTS to words("Ingredients", "Ingredientes", "Zutaten", "Ingrédients", "Ingredienti", "Ingredients list"),
        Section.STEPS to words(
            "Steps", "Instructions", "Method", "Directions", "Preparation", "Procedure",
            "Pasos", "Instrucciones", "Preparación", "Preparacion", "Elaboración", "Elaboracion",
            "Zubereitung", "Anleitung", "Schritte", "Préparation", "Étapes", "Etapes", "Preparazione", "Procedimento", "Passaggi",
        ),
        Section.NOTES to words("Notes", "Note", "Tips", "Tip", "Notas", "Consejos", "Hinweise", "Tipps", "Remarques", "Conseils"),
    )

    private fun words(vararg names: String): Regex {
        val alternatives = names.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        // Optional Markdown decoration (#, *, _) around the header, then ":" or
        // a full-width colon, then the inline value (may be empty).
        return Regex("^\\s*[#*_\\s]*(?:$alternatives)[*_\\s]*[:：]\\s*(.*?)\\s*$", RegexOption.IGNORE_CASE)
    }

    private val bullet = Regex("^\\s*[-*•–·]\\s+(.*?)\\s*$")

    /**
     * `1.`, `1)`, `1:`, `Step 1:` list markers. A dot that is directly
     * followed by a digit is a decimal point, not a marker: `250.0g potatoes`
     * and `2.5 cups flour` are quantities, `2.Preheat…` is still step 2.
     */
    private val numbered = Regex("^\\s*(?:Step\\s*)?\\d{1,3}\\s*(?:[):]|\\.(?!\\d))\\s*(.*?)\\s*$", RegexOption.IGNORE_CASE)
    private val bareNumber = Regex("^\\s*(?:Step\\s*)?\\d{1,3}\\s*[.):]?\\s*$", RegexOption.IGNORE_CASE)
    private val markdownDecor = Regex("^[*_`]+|[*_`]+$")

    /** Parses [lines] (already complete lines; no trailing partial). */
    fun parse(lines: List<String>): Parsed {
        var section = Section.NONE
        var title: String? = null
        var description = StringBuilder()
        var servings: String? = null
        var prep: Int? = null
        var cook: Int? = null
        var total: Int? = null
        val ingredients = mutableListOf<String>()
        val steps = mutableListOf<String>()
        val notes = StringBuilder()
        var lastLine = ""

        fun scalar(current: String?, value: String): String? =
            if (value.isBlank()) current else current?.takeIf { it.isNotBlank() } ?: value

        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) continue
            lastLine = line

            val header = matchHeader(line)
            if (header != null) {
                val (newSection, inline) = header
                section = newSection
                val value = undecorate(inline)
                when (newSection) {
                    Section.TITLE -> title = scalar(title, value)
                    Section.DESCRIPTION -> if (value.isNotBlank()) { description = StringBuilder(value) }
                    Section.SERVINGS -> servings = scalar(servings, value)
                    Section.PREP -> prep = prep ?: parseMinutes(value)
                    Section.COOK -> cook = cook ?: parseMinutes(value)
                    Section.TOTAL -> total = total ?: parseMinutes(value)
                    Section.INGREDIENTS, Section.STEPS -> {
                        // "Ingredients: flour, sugar" on one line — rare, accept.
                        if (value.isNotBlank()) {
                            val target = if (newSection == Section.INGREDIENTS) ingredients else steps
                            target += value
                        }
                    }
                    Section.NOTES -> if (value.isNotBlank()) notes.append(value)
                    Section.NONE -> Unit
                }
                continue
            }

            when (section) {
                Section.INGREDIENTS -> addListItem(line, ingredients, allowNumbered = true)
                Section.STEPS -> addListItem(line, steps, allowNumbered = true)
                Section.DESCRIPTION -> appendText(description, line)
                Section.NOTES -> appendText(notes, undecorate(line))
                Section.TITLE -> if (title.isNullOrBlank()) title = undecorate(line)
                Section.SERVINGS -> if (servings.isNullOrBlank()) servings = undecorate(line)
                Section.PREP -> if (prep == null) prep = parseMinutes(line)
                Section.COOK -> if (cook == null) cook = parseMinutes(line)
                Section.TOTAL -> if (total == null) total = parseMinutes(line)
                Section.NONE -> Unit // preamble chatter before the first header
            }
        }

        return Parsed(
            title = title?.takeIf { it.isNotBlank() },
            description = description.toString().trim().takeIf { it.isNotBlank() },
            servings = servings?.takeIf { it.isNotBlank() },
            prepMinutes = prep,
            cookMinutes = cook,
            totalMinutes = total,
            ingredients = ingredients.filter { it.isNotBlank() },
            steps = steps.filter { it.isNotBlank() },
            notes = notes.toString().trim().takeIf { it.isNotBlank() },
            lastSection = section,
            lastLine = lastLine,
        )
    }

    /** `true` when [line] is one of the recognised section headers. */
    fun isHeader(line: String): Boolean = matchHeader(line) != null

    private fun matchHeader(line: String): Pair<Section, String>? {
        for ((section, regex) in headerSynonyms) {
            val m = regex.matchEntire(line) ?: continue
            return section to m.groupValues[1]
        }
        return null
    }

    private fun addListItem(line: String, target: MutableList<String>, allowNumbered: Boolean) {
        bullet.matchEntire(line)?.let { m ->
            val text = undecorate(stripNumber(m.groupValues[1]))
            if (text.isNotBlank()) target += text
            return
        }
        if (allowNumbered) {
            if (bareNumber.matches(line)) return // "3." alone: the text is on the next line
            numbered.matchEntire(line)?.let { m ->
                val text = undecorate(m.groupValues[1])
                if (text.isNotBlank()) target += text
                return
            }
        }
        // Continuation of the previous item (wrapped step text), or a first
        // item without a bullet.
        val text = undecorate(line)
        if (text.isBlank()) return
        if (target.isEmpty()) target += text else target[target.lastIndex] = target.last() + " " + text
    }

    private fun stripNumber(text: String): String =
        numbered.matchEntire(text)?.groupValues?.get(1) ?: text

    private fun appendText(builder: StringBuilder, line: String) {
        val text = line.trim()
        if (text.isBlank()) return
        if (builder.isNotEmpty()) builder.append(' ')
        builder.append(text)
    }

    private fun undecorate(text: String): String = text.trim().replace(markdownDecor, "").trim()

    /**
     * "45" → 45, "45 minutes" → 45, "1 hour 15 min" → 75, "1h30" → 90,
     * "PT1H30M" → 90, "about 20-25 min" → 25 (ranges → max). `null` when no
     * number is present or the value is "unknown"/"N/A".
     */
    fun parseMinutes(value: String): Int? {
        val text = value.trim().lowercase()
        if (text.isEmpty()) return null
        if (text.startsWith("pt") || text.startsWith("p0d") || text.startsWith("p") && text.contains('t')) {
            val h = Regex("(\\d+)h").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val m = Regex("(\\d+)m").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val d = Regex("p(\\d+)d").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            return (d * 24 * 60 + h * 60 + m).takeIf { it > 0 }
        }
        val hours = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:hours?|hrs?|h(?=\\s|\\d|$)|std|stunden?|horas?|heures?|ora|ore)").find(text)
        val mins = Regex("(\\d+)\\s*(?:m\\b|min|mins|minute|minutes|minuto|minutos|minuti)").find(text)
        if (hours != null || mins != null) {
            val h = hours?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            val m = mins?.groupValues?.get(1)?.toIntOrNull() ?: 0
            // "1h30" with no unit on the minutes part
            val compact = Regex("(\\d+)\\s*h\\s*(\\d{1,2})\\b").find(text)
            val m2 = if (mins == null && compact != null) compact.groupValues[2].toInt() else 0
            return Math.round(h * 60).toInt().plus(m).plus(m2).takeIf { it > 0 }
        }
        // Bare numbers (possibly a range): take the largest.
        val numbers = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
        return numbers.maxOrNull()?.takeIf { it > 0 }
    }
}
