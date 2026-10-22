package com.llmlocal.recipekeeper.clip.domain

import com.llmlocal.core.web.IsoDuration
import com.llmlocal.core.web.JsonLdNode
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Deterministic `schema.org/Recipe` JSON-LD → [ClippedRecipe] mapping.
 *
 * Used twice: as the LLM-free [RecipeKeeperClipConfig.fallback] for
 * STRUCTURED candidates, and (field by field) to render the compact LLM
 * input and the candidate card. Handles every field shape listed in the
 * research notes §B:
 *
 *  - `image`: string | string[] | ImageObject{url,contentUrl} | ImageObject[]
 *  - `recipeInstructions`: Text | Text[] | HowToStep[] | HowToSection[] |
 *    ItemList — flattened, HTML stripped, duplicated step names removed
 *  - `recipeIngredient` (legacy `ingredients`): string | string[]
 *  - `recipeYield`: Text | number | array | QuantitativeValue
 *  - `prepTime` / `cookTime` / `totalTime`: ISO-8601 via [durationParser]
 *
 * @param untitledTitle title used when the node has no name (user-visible; from resources)
 * @param durationParser ISO/free-text duration → minutes. Defaults to
 *   `IsoDuration.toMinutes` from `:core:web` (implemented by WS2); tests
 *   pass their own lambda.
 */
class SchemaOrgRecipeMapper(
    private val untitledTitle: String,
    private val durationParser: (String?) -> Int? = IsoDuration::toMinutes,
) {

    /**
     * @return the mapped recipe, or `null` when the node has neither
     *   ingredients nor instructions (nothing worth saving). `pictures` is
     *   always empty here: local paths only exist after download.
     */
    fun map(
        node: JsonLdNode,
        sourceUrl: String,
        id: String = UUID.randomUUID().toString(),
        clippedAt: Long = System.currentTimeMillis(),
    ): ClippedRecipe? {
        val raw = node.raw
        val ingredients = ingredients(raw)
        val steps = instructions(raw)
        if (ingredients.isEmpty() && steps.isEmpty()) return null

        val prep = minutes(raw, "prepTime")
        val cook = minutes(raw, "cookTime")
        return ClippedRecipe(
            id = id,
            title = name(raw).ifBlank { untitledTitle },
            description = description(raw),
            servings = yieldText(raw),
            prepMinutes = prep,
            cookMinutes = cook,
            totalMinutes = totalMinutes(raw, prep, cook),
            ingredients = ingredients,
            steps = steps,
            notes = null,
            sourceUrl = sourceUrl,
            sourceHost = TextCleaner.host(sourceUrl),
            pictures = emptyList(),
            truncated = false,
            clippedAt = clippedAt,
        )
    }

    // -----------------------------------------------------------------------
    // Field accessors (also used by RecipeKeeperClipConfig)
    // -----------------------------------------------------------------------

    fun name(raw: JSONObject): String = TextCleaner.oneLine(raw.optStringOrNull("name") ?: raw.optStringOrNull("headline"))

    fun description(raw: JSONObject): String? =
        TextCleaner.oneLine(raw.optStringOrNull("description")).takeIf { it.isNotBlank() }

    /** Ingredient lines, HTML stripped, `250.0g` tidied to `250g`, blanks removed. */
    fun ingredients(raw: JSONObject): List<String> {
        val value = raw.opt("recipeIngredient") ?: raw.opt("ingredients") ?: return emptyList()
        return flattenStrings(value)
            .map { TextCleaner.tidyNumbers(TextCleaner.oneLine(it)) }
            .filter { it.isNotBlank() }
            .flatMap(::splitJoinedIngredient)
    }

    /**
     * Some sites emit the whole list as one dash-joined string
     * (`150grs de farina-75grs de margarina-…`, `a - b - c`). Split it when
     * that yields at least [MIN_JOINED_PARTS] parts; otherwise keep the line.
     */
    private fun splitJoinedIngredient(line: String): List<String> {
        val spaced = line.split(spacedDash).map { it.trim() }.filter { it.isNotEmpty() }
        if (spaced.size >= MIN_JOINED_PARTS) return spaced
        val glued = line.split(gluedDash).map { it.trim().trimEnd('-').trim() }.filter { it.isNotEmpty() }
        if (glued.size >= MIN_JOINED_PARTS) return glued
        return listOf(line)
    }

    /** Instruction steps flattened from any of the schema.org shapes. */
    fun instructions(raw: JSONObject): List<String> {
        val value = raw.opt("recipeInstructions") ?: return emptyList()
        val out = mutableListOf<String>()
        collectSteps(value, out)
        return out.filter { it.isNotBlank() }
    }

    /** Image URLs in document order, http(s) only, de-duplicated. */
    fun imageUrls(raw: JSONObject): List<String> {
        val value = raw.opt("image") ?: raw.opt("thumbnailUrl") ?: return emptyList()
        val out = mutableListOf<String>()
        collectImages(value, out)
        return out.filter { TextCleaner.isHttpUrl(it) }.distinctBy { TextCleaner.urlKey(it) }
    }

    /** `recipeYield` as display text ("4", "6 servings", "12 cookies"; `10.0 items` → `10 items`), or `null`. */
    fun yieldText(raw: JSONObject): String? {
        val value = raw.opt("recipeYield") ?: raw.opt("yield") ?: return null
        return yieldOf(value)?.let { TextCleaner.tidyNumbers(it) }?.takeIf { it.isNotBlank() }
    }

    fun minutes(raw: JSONObject, key: String): Int? {
        val value = raw.opt(key) ?: return null
        val text = when (value) {
            is String -> value
            is JSONObject -> value.optStringOrNull("value") ?: value.optStringOrNull("duration")
            is Number -> return value.toInt().takeIf { it > 0 }
            else -> null
        } ?: return null
        return durationParser(text)?.takeIf { it > 0 }
    }

    /** `totalTime`, else prep + cook when at least one of them is known. */
    fun totalMinutes(raw: JSONObject, prep: Int? = minutes(raw, "prepTime"), cook: Int? = minutes(raw, "cookTime")): Int? {
        minutes(raw, "totalTime")?.let { return it }
        if (prep == null && cook == null) return null
        return (prep ?: 0) + (cook ?: 0)
    }

    // -----------------------------------------------------------------------
    // Shape flattening
    // -----------------------------------------------------------------------

    private fun flattenStrings(value: Any): List<String> = when (value) {
        is String -> value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        is JSONArray -> (0 until value.length()).flatMap { i -> value.opt(i)?.let { flattenStrings(it) } ?: emptyList() }
        is JSONObject -> listOfNotNull(value.optStringOrNull("text") ?: value.optStringOrNull("name"))
        is Number, is Boolean -> listOf(value.toString())
        else -> emptyList()
    }

    private fun collectSteps(value: Any, out: MutableList<String>) {
        when (value) {
            is String -> out += splitTextSteps(TextCleaner.clean(value))
            is JSONArray -> for (i in 0 until value.length()) value.opt(i)?.let { collectSteps(it, out) }
            is JSONObject -> {
                val types = typesOf(value)
                val list = value.opt("itemListElement")
                when {
                    // HowToSection / ItemList: recurse into children. A section
                    // name is navigation, not a step, so it is dropped.
                    list != null -> collectSteps(list, out)
                    types.any { it.equals("HowToSection", true) || it.equals("ItemList", true) } -> Unit
                    else -> stepText(value)?.let { out += it }
                }
            }
        }
    }

    /** HowToStep → text; strips a `name` duplicated at the start of `text`. */
    private fun stepText(step: JSONObject): String? {
        val text = TextCleaner.oneLine(step.optStringOrNull("text"))
        val name = TextCleaner.oneLine(step.optStringOrNull("name"))
        val body = when {
            text.isNotBlank() && name.isNotBlank() && duplicatesName(text, name) ->
                text.drop(name.length).trimStart(' ', ':', '-', '–', '.', ',')
            text.isNotBlank() -> text
            name.isNotBlank() -> name
            else -> return null
        }
        return body.ifBlank { text.ifBlank { name } }.takeIf { it.isNotBlank() }
    }

    /**
     * `true` when [text] repeats [name] as a label: identical, or [name]
     * followed by a delimiter (`Mix wet ingredients: stir…`). A name that is
     * simply the first word of the sentence (`Preheat` / `Preheat the oven`)
     * is not a duplicate.
     */
    private fun duplicatesName(text: String, name: String): Boolean {
        if (!text.startsWith(name, ignoreCase = true)) return false
        if (text.length == name.length) return true
        return text[name.length] in ":-–.,;\n"
    }

    /**
     * A single Text instruction often holds every step, separated by
     * newlines or by `1. … 2. …` numbering. Split on newlines first; if
     * that leaves one long line with inline numbering, split on the
     * numbers.
     */
    private fun splitTextSteps(text: String): List<String> {
        val lines = text.split('\n').map { it.trim().trimStart('-', '•', '*').trim() }.filter { it.isNotEmpty() }
        if (lines.size > 1) return lines.map(::stripLeadingNumber)
        val single = lines.firstOrNull() ?: return emptyList()
        val parts = single.split(numberedStep).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size > 1) return parts
        val one = stripLeadingNumber(single)
        return if (one.length > BLOB_CHARS) one.split(sentenceBoundary).map { it.trim() }.filter { it.isNotEmpty() } else listOf(one)
    }

    private fun stripLeadingNumber(step: String): String =
        step.replace(Regex("^(?:Step\\s*)?\\d{1,2}\\s*[.):]\\s*", RegexOption.IGNORE_CASE), "").trim()

    private fun collectImages(value: Any, out: MutableList<String>) {
        when (value) {
            is String -> if (value.isNotBlank()) out += value.trim()
            is JSONArray -> for (i in 0 until value.length()) value.opt(i)?.let { collectImages(it, out) }
            is JSONObject -> {
                val url = value.optStringOrNull("url") ?: value.optStringOrNull("contentUrl")
                    ?: value.optStringOrNull("@id")
                if (url != null) out += url.trim()
            }
        }
    }

    private fun yieldOf(value: Any): String? = when (value) {
        is String -> TextCleaner.oneLine(value)
        is Number -> value.toString().removeSuffix(".0")
        is JSONArray -> {
            // Sites emit ["4", "4 servings"]; prefer the entry with a unit.
            val entries = (0 until value.length()).mapNotNull { i -> value.opt(i)?.let(::yieldOf) }.filter { it.isNotBlank() }
            entries.firstOrNull { it.any(Char::isLetter) } ?: entries.firstOrNull()
        }
        is JSONObject -> {
            val amount = value.opt("value")?.toString()?.removeSuffix(".0")
            val unit = value.optStringOrNull("unitText") ?: value.optStringOrNull("unitCode")
            listOfNotNull(amount, unit).joinToString(" ").ifBlank { value.optStringOrNull("name") }
        }
        else -> null
    }

    private fun typesOf(obj: JSONObject): List<String> = when (val t = obj.opt("@type")) {
        is String -> listOf(t)
        is JSONArray -> (0 until t.length()).mapNotNull { t.optString(it).takeIf(String::isNotBlank) }
        else -> emptyList()
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val value = opt(key) ?: return null
        if (value == JSONObject.NULL) return null
        return when (value) {
            is String -> value.takeIf { it.isNotBlank() }
            is JSONObject, is JSONArray -> null
            else -> value.toString()
        }
    }

    private companion object {
        const val MIN_JOINED_PARTS = 3
        /** A single un-numbered instruction longer than this is split into sentences. */
        const val BLOB_CHARS = 200
        val spacedDash = Regex("\\s+[-–]\\s+")
        val gluedDash = Regex("(?<=\\p{L})-(?=\\d)")
        val numberedStep = Regex("(?:(?<=^)|(?<=[.!?]\\s))\\d{1,2}[.)]\\s+")
        val sentenceBoundary = Regex("(?<=[.!?])\\s+(?=[\\p{Lu}¡¿\\d])")
    }
}
