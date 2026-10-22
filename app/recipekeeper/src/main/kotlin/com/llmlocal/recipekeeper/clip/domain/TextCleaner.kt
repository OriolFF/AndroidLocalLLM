package com.llmlocal.recipekeeper.clip.domain

/**
 * Small text helpers shared by the schema.org mapper, the clip config and
 * the parsers. Deliberately regex-only (no HTML parser dependency): the
 * inputs are short field values, not whole documents.
 */
internal object TextCleaner {

    private val tags = Regex("<[^>]+>")
    private val whitespace = Regex("[ \\t\\u00A0]+")
    private val blankLines = Regex("\\n\\s*\\n(\\s*\\n)+")
    private val numericEntity = Regex("&#(x[0-9a-fA-F]+|\\d+);")
    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
        "deg" to "°", "ndash" to "–", "mdash" to "—", "hellip" to "…",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“",
    )
    private val namedEntity = Regex("&([a-zA-Z]+);")

    /** Strips tags, decodes common entities, collapses runs of spaces. Keeps newlines. */
    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var text = raw.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|li)>", RegexOption.IGNORE_CASE), "\n")
            .replace(tags, "")
        text = numericEntity.replace(text) { m ->
            val value = m.groupValues[1]
            val code = if (value.startsWith("x", ignoreCase = true)) value.drop(1).toIntOrNull(16) else value.toIntOrNull()
            code?.let { String(Character.toChars(it)) } ?: m.value
        }
        text = namedEntity.replace(text) { m -> namedEntities[m.groupValues[1]] ?: m.value }
        text = text.replace("\r\n", "\n").replace('\r', '\n')
        text = whitespace.replace(text, " ")
        text = text.lines().joinToString("\n") { it.trim() }
        text = blankLines.replace(text, "\n\n")
        return text.trim()
    }

    /** [clean] and then flatten to one line. */
    fun oneLine(raw: String?): String = clean(raw).replace(Regex("\\s*\\n\\s*"), " ").trim()

    private val trailingZero = Regex("(\\d+)\\.0(?!\\d)")

    /** `250.0g` → `250g`, `Makes 10.0 items` → `Makes 10 items`; `0.05` and `6.75` are untouched. */
    fun tidyNumbers(text: String): String = trailingZero.replace(text, "$1")

    private val units = listOf(
        // EN
        "g", "gr", "grs", "gm", "kg", "mg", "ml", "l", "dl", "cl", "cup", "cups", "tbsp", "tbs", "tsp", "tablespoon", "tablespoons", "teaspoon", "teaspoons",
        "oz", "ounce", "ounces", "lb", "lbs", "pound", "pounds", "pinch", "dash", "clove", "cloves", "slice", "slices", "can", "cans", "stick", "sticks",
        "bunch", "handful", "piece", "pieces", "level", "heaped", "large", "medium", "small",
        // ES
        "taza", "tazas", "cucharada", "cucharadas", "cucharadita", "cucharaditas", "gramo", "gramos", "litro", "litros", "pizca", "diente", "dientes", "rebanada", "rebanadas",
        // CA
        "cullerada", "culleradas", "culleradeta", "culleradetes", "tassa", "tasses", "pessic", "rovell", "rovells", "gra", "grams",
        // DE
        "el", "tl", "tasse", "tassen", "prise", "stück", "becher", "pck", "pkt", "dose", "bund", "zehe", "zehen", "scheibe", "scheiben", "msp", "esslöffel", "teelöffel",
        // FR
        "cuillère", "cuillères", "cuillere", "cuilleres", "cuil", "càs", "càc", "cas", "cac", "pincée", "pincées", "gousse", "gousses", "sachet", "sachets", "tranche", "tranches", "verre", "verres",
        // IT
        "cucchiaio", "cucchiai", "cucchiaino", "cucchiaini", "tazza", "tazze", "pizzico", "spicchio", "spicchi", "fetta", "fette", "bicchiere", "bicchieri", "mazzetto", "bustina", "bustine",
    ).distinct().sortedByDescending { it.length }
    private val gluedUnit = Regex(
        "(?<![\\p{L}\\d.,])([\\d½¼¾⅓⅔⅛]+(?:[.,/][\\d]+)?)(" + units.joinToString("|") + ")(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )

    /**
     * `2tablespoon` → `2 tablespoon`, `1level tsp` → `1 level tsp`,
     * `150grs de farina` → `150 grs de farina`: a quantity glued to a known
     * unit word (EN/ES/CA/DE/FR/IT). Unknown suffixes (`2nd`, `3D`) are left alone.
     */
    fun separateUnits(text: String): String = gluedUnit.replace(text) { m -> m.groupValues[1] + " " + m.groupValues[2] }

    /** URL without query / fragment, for de-duplication. */
    fun urlKey(url: String): String = url.substringBefore('#').substringBefore('?').trimEnd('/')

    fun isHttpUrl(url: String?): Boolean =
        url != null && (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))

    /** Host of [url] without a leading `www.`; empty when unparseable. */
    fun host(url: String): String = runCatching { java.net.URI(url).host.orEmpty() }
        .getOrDefault("")
        .removePrefix("www.")

    private val firstSentence = Regex("(?<=[.!?])\\s+")
    private val alternative = Regex("\\s*(?:[;,]\\s*)?\\bor\\b\\s+|\\s*;\\s*", RegexOption.IGNORE_CASE)

    /**
     * Servings as a chip-sized label: the first sentence, cut before an
     * alternative ("16 cookies or 20 large ones"), at most [SERVINGS_MAX_CHARS]
     * characters (word boundary + `…`). The full sentence is not kept.
     */
    fun shortServings(text: String?): String? {
        val flat = oneLine(text).ifBlank { return null }
        val sentence = flat.split(firstSentence).first().trimEnd('.', ',', ';')
        val head = sentence.split(alternative, limit = 2).first().trim().ifBlank { sentence }
        if (head.length <= SERVINGS_MAX_CHARS) return head
        val cut = head.take(SERVINGS_MAX_CHARS)
        val space = cut.lastIndexOf(' ')
        return (if (space > SERVINGS_MAX_CHARS / 2) cut.substring(0, space) else cut).trimEnd(',', ';', ' ', '-') + "…"
    }

    const val SERVINGS_MAX_CHARS = 40
}
