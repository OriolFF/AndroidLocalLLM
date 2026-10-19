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

    /** URL without query / fragment, for de-duplication. */
    fun urlKey(url: String): String = url.substringBefore('#').substringBefore('?').trimEnd('/')

    fun isHttpUrl(url: String?): Boolean =
        url != null && (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))

    /** Host of [url] without a leading `www.`; empty when unparseable. */
    fun host(url: String): String = runCatching { java.net.URI(url).host.orEmpty() }
        .getOrDefault("")
        .removePrefix("www.")
}
