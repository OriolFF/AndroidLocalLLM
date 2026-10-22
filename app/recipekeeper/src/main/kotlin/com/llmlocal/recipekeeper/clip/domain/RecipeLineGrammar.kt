package com.llmlocal.recipekeeper.clip.domain

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
 * TotalMinutes: <int>
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
 * `PrepMinutes: 1 h 15 min` style durations. An explicit `TotalMinutes:`
 * wins over `prep + cook` in [RecipeKeeperParser]. A `Notes:` line that
 * merely repeats a sentence already present in a step is dropped (models
 * like to echo "do not overbake" style advice).
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
        Section.TITLE to words("Title", "Name", "Recipe", "Recipe name", "Recipe title", "Titre", "Título", "Titulo", "Titel", "Titolo", "Títol", "Τίτλος"),
        Section.DESCRIPTION to words("Description", "Summary", "Descripción", "Descripcion", "Beschreibung", "Descrizione", "Descripció", "Περιγραφή"),
        Section.SERVINGS to words("Servings", "Serves", "Yield", "Yields", "Portions", "Porciones", "Raciones", "Portionen", "Porzioni", "Racions", "Personas", "Persones", "Comensals", "Comensales", "Μερίδες", "Άτομα", "Ατομα"),
        Section.PREP to words("PrepMinutes", "Prep Minutes", "Prep time", "Preparation time", "Prep", "Tiempo de preparación", "Vorbereitungszeit", "Temps de préparation", "Temps de preparació", "Tiempo de preparacion", "Χρόνος προετοιμασίας", "Προετοιμασία"),
        Section.COOK to words("CookMinutes", "Cook Minutes", "Cook time", "Cooking time", "Cook", "Tiempo de cocción", "Kochzeit", "Temps de cuisson", "Tiempo de cocinado", "Temps de cocció", "Χρόνος μαγειρέματος", "Μαγείρεμα"),
        Section.TOTAL to words("TotalMinutes", "Total Minutes", "Total time", "Total", "Tiempo total", "Gesamtzeit", "Temps total", "Συνολικός χρόνος"),
        Section.INGREDIENTS to words("Ingredients", "Ingredientes", "Zutaten", "Ingrédients", "Ingredienti", "Ingredients list", "Υλικά", "Συστατικά"),
        Section.STEPS to words(
            "Steps", "Instructions", "Method", "Directions", "Preparation", "Procedure",
            "Pasos", "Instrucciones", "Preparación", "Preparacion", "Elaboración", "Elaboracion",
            "Zubereitung", "Anleitung", "Schritte", "Préparation", "Étapes", "Etapes", "Preparazione", "Procedimento", "Passaggi",
            "Paso a paso", "Modo de preparación", "Modo de preparacion", "Procedimiento", "Elaboració", "Preparació", "Pas a pas", "Com es fa", "Instruccions", "Procediment",
            "Εκτέλεση", "Διαδικασία", "Οδηγίες", "Βήματα", "Παρασκευή", "Μέθοδος",
        ),
        Section.NOTES to words("Notes", "Note", "Tips", "Tip", "Notas", "Consejos", "Hinweise", "Tipps", "Remarques", "Conseils", "Consells", "Σημειώσεις", "Συμβουλές"),
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
                    Section.NOTES -> if (value.isNotBlank() && !duplicatesStep(value, steps)) notes.append(value)
                    Section.NONE -> Unit
                }
                continue
            }

            when (section) {
                Section.INGREDIENTS -> addListItem(line, ingredients, allowNumbered = true)
                Section.STEPS -> addListItem(line, steps, allowNumbered = true)
                Section.DESCRIPTION -> appendText(description, line)
                Section.NOTES -> undecorate(line).let { note -> if (!duplicatesStep(note, steps)) appendText(notes, note) }
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

    /**
     * `true` when [note] (bullet/number stripped, punctuation and case
     * ignored, at least three words) is contained in one of [steps].
     */
    private fun duplicatesStep(note: String, steps: List<String>): Boolean {
        val needle = normaliseForCompare(note.replace(listMarker, ""))
        if (needle.isEmpty() || needle.count { it == ' ' } < 2) return false
        return steps.any { normaliseForCompare(it).contains(needle) }
    }

    private val listMarker = Regex("^\\s*(?:[-*•–·]|(?:Step\\s*)?\\d{1,3}\\s*(?:[):]|\\.(?!\\d)))\\s*", RegexOption.IGNORE_CASE)
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")

    private fun normaliseForCompare(text: String): String = text.lowercase().replace(nonWord, " ").trim()

    private fun appendText(builder: StringBuilder, line: String) {
        val text = line.trim()
        if (text.isBlank()) return
        if (builder.isNotEmpty()) builder.append(' ')
        builder.append(text)
    }

    private fun undecorate(text: String): String = text.trim().replace(markdownDecor, "").trim()

    /**
     * "45" → 45, "45 minutes" → 45, "1 hour 15 min" → 75, "1h30" → 90,
     * "PT1H30M" → 90, "about 20-25 min" → 25, "1-1¼ hours" → 75, "1½ h" → 90
     * (ranges → max; vulgar fractions and `1 1/4` mixed numbers are
     * normalised to decimals first). `null` when no number is present or
     * the value is "unknown"/"N/A".
     */
    fun parseMinutes(value: String): Int? {
        val raw = value.trim().lowercase()
        if (raw.isEmpty()) return null
        if (raw.startsWith("pt") || raw.startsWith("p0d") || raw.startsWith("p") && raw.contains('t')) {
            val h = Regex("(\\d+)h").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val m = Regex("(\\d+)m").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val d = Regex("p(\\d+)d").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            return (d * 24 * 60 + h * 60 + m).takeIf { it > 0 }
        }
        val text = normaliseFractions(raw)
        val hourUnit = "(?:hours?|hrs?|h(?=\\s|\\d|$)|std|stunden?|horas?|heures?|ora|ore)"
        val minuteUnit = "(?:m\\b|min|mins|minute|minutes|minuto|minutos|minuti)"
        // Every "<number> <unit>" occurrence; a range ("1-1.25 hours", "20-25 min") keeps the largest.
        val hours = Regex("(\\d+(?:[.,]\\d+)?)\\s*$hourUnit").findAll(text)
            .mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }.maxOrNull()
        val mins = Regex("(\\d+)\\s*$minuteUnit").findAll(text)
            .mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()
        if (hours != null || mins != null) {
            val h = hours ?: 0.0
            val m = mins ?: 0
            // "1h30" with no unit on the minutes part
            val compact = Regex("(\\d+)\\s*h\\s*(\\d{1,2})\\b").find(text)
            val m2 = if (mins == null && compact != null) compact.groupValues[2].toInt() else 0
            return Math.round(h * 60).toInt().plus(m).plus(m2).takeIf { it > 0 }
        }
        // Bare numbers (possibly a range): take the largest.
        val numbers = Regex("\\d+(?:\\.\\d+)?").findAll(text).mapNotNull { it.value.toDoubleOrNull() }.toList()
        return numbers.maxOrNull()?.let { Math.round(it).toInt() }?.takeIf { it > 0 }
    }

    private val vulgarFractions = mapOf('¼' to 0.25, '½' to 0.5, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅛' to 0.125)

    /**
     * `1¼` / `1 ½` → `1.25` / `1.5`, `½` → `0.5`, `1 1/4` → `1.25`, `1/2` → `0.5`,
     * so the unit regexes only ever see decimals.
     */
    internal fun normaliseFractions(text: String): String {
        var t = Regex("(\\d+)\\s*([¼½¾⅓⅔⅛])").replace(text) { m ->
            decimal(m.groupValues[1].toInt() + vulgarFractions.getValue(m.groupValues[2][0]))
        }
        t = Regex("[¼½¾⅓⅔⅛]").replace(t) { m -> decimal(vulgarFractions.getValue(m.value[0])) }
        t = Regex("(\\d+)\\s+(\\d+)/(\\d+)").replace(t) { m ->
            val den = m.groupValues[3].toInt()
            if (den == 0) m.value else decimal(m.groupValues[1].toInt() + m.groupValues[2].toInt().toDouble() / den)
        }
        t = Regex("(?<![\\d.])(\\d+)/(\\d+)").replace(t) { m ->
            val den = m.groupValues[2].toInt()
            if (den == 0) m.value else decimal(m.groupValues[1].toInt().toDouble() / den)
        }
        return t
    }

    private fun decimal(value: Double): String = "%.3f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')
}
