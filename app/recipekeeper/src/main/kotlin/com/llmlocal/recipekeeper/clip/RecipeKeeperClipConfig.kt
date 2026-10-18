package com.llmlocal.recipekeeper.clip

import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.web.IsoDuration
import com.llmlocal.core.web.JsonLdNode
import com.llmlocal.core.web.PageImage
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.core.web.TextSection
import com.llmlocal.feature.webclipper.CandidatePayload
import com.llmlocal.feature.webclipper.CandidateSource
import com.llmlocal.feature.webclipper.ClipCandidate
import com.llmlocal.feature.webclipper.WebClipConfig
import com.llmlocal.recipekeeper.domain.ClippedRecipe
import org.json.JSONObject
import kotlin.math.abs

/**
 * Recipe Keeper's [WebClipConfig]: how a captured page becomes recipe
 * candidates, LLM input, a deterministic fallback and a picture list
 * (plan §3.2, §3.3, §3.6).
 *
 * **Detect.** Tier A: every JSON-LD node typed `Recipe` (or whose
 * `mainEntity` is one) is a STRUCTURED candidate. Tier B (only when Tier A
 * finds nothing): a heading section is a TEXT candidate when its text has
 * at least [MIN_QUANTITY_LINES] quantity-unit lines, or its heading is an
 * "ingredients" word and an "instructions" word follows within the next
 * sections. A recipe-ish URL path relaxes the line threshold by one. When
 * neither tier finds anything and the readable text is at least
 * [MIN_WHOLE_PAGE_CHARS] long, a single `WholePage` candidate is offered
 * ("Extract anyway" also uses it). Ranking: STRUCTURED first in document
 * order, then TEXT by heading level, then position. Capped at 10.
 *
 * **Render.** STRUCTURED → compact field rendering (`Name / Description /
 * Yield / Times / Ingredients / Instructions / Images: n`). TEXT → the
 * section plus the sections nested under it (so "Ingredients" h3 pulls in
 * the sibling "Method" h3). WholePage → `readable.textContent`. All three
 * start with `Page: <host> — <title>` and are trimmed to `budgetChars` at a
 * paragraph boundary with a `[…truncated]` marker.
 *
 * @param durationParser ISO-8601/free-text → minutes; `IsoDuration.toMinutes`
 *   (WS2) in production, a lambda in tests
 * @param idFactory id for saved recipes (the fallback path); UUID by default
 * @param clock epoch millis for `clippedAt`
 */
class RecipeKeeperClipConfig(
    durationParser: (String?) -> Int? = IsoDuration::toMinutes,
    private val mapper: SchemaOrgRecipeMapper = SchemaOrgRecipeMapper(durationParser),
    private val idFactory: () -> String = { java.util.UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) : WebClipConfig<ClippedRecipe> {

    override val llmConfig: LlmTaskConfig<ClippedRecipe> = RecipeKeeperTaskConfig

    // -----------------------------------------------------------------------
    // Detect
    // -----------------------------------------------------------------------

    override fun detect(snapshot: PageSnapshot): List<ClipCandidate> {
        val structured = detectStructured(snapshot)
        if (structured.isNotEmpty()) return structured.take(MAX_CANDIDATES)

        val text = detectText(snapshot)
        if (text.isNotEmpty()) return text.take(MAX_CANDIDATES)

        return listOfNotNull(wholePageCandidate(snapshot))
    }

    private fun detectStructured(snapshot: PageSnapshot): List<ClipCandidate> {
        val seen = HashSet<String>()
        return snapshot.jsonLd
            .mapNotNull { node -> recipeNode(node) }
            .filter { node ->
                val raw = node.raw
                val key = mapper.name(raw).lowercase() + "|" + mapper.ingredients(raw).size + "|" + mapper.instructions(raw).size
                seen.add(key)
            }
            .mapIndexed { index, node ->
                val raw = node.raw
                val ingredients = mapper.ingredients(raw)
                val title = mapper.name(raw).ifBlank { snapshot.title?.trim().orEmpty() }.ifBlank { DEFAULT_TITLE }
                ClipCandidate(
                    id = "structured-$index",
                    title = title,
                    thumbnailUrl = mapper.imageUrls(raw).firstOrNull(),
                    snippet = mapper.description(raw)?.let { snippet(it) },
                    source = CandidateSource.STRUCTURED,
                    ingredientCount = ingredients.size.takeIf { it > 0 },
                    totalMinutes = mapper.totalMinutes(raw),
                    payload = CandidatePayload.Structured(node),
                )
            }
    }

    /** The node itself when typed Recipe, else its `mainEntity` when that is. */
    private fun recipeNode(node: JsonLdNode): JsonLdNode? {
        if (node.types.any(::isRecipeType)) return node
        val main = node.raw.optJSONObject("mainEntity") ?: return null
        val mainTypes = typesOf(main)
        return if (mainTypes.any(::isRecipeType)) JsonLdNode(mainTypes, main) else null
    }

    private fun detectText(snapshot: PageSnapshot): List<ClipCandidate> {
        val sections = snapshot.sections
        if (sections.isEmpty()) return emptyList()
        val threshold = if (hasRecipePath(snapshot.url)) MIN_QUANTITY_LINES - 1 else MIN_QUANTITY_LINES

        val picked = mutableListOf<Pair<Int, TextSection>>()
        var coveredLevel: Int? = null // level of the last candidate; deeper sections until a shallower heading are "covered"
        sections.forEachIndexed { index, section ->
            if (coveredLevel != null && section.level <= coveredLevel!!) coveredLevel = null
            if (coveredLevel != null) return@forEachIndexed
            if (isRecipeSection(sections, index, threshold)) {
                picked += index to section
                coveredLevel = section.level
            }
        }

        return picked
            .sortedWith(compareBy({ it.second.level }, { it.second.topOffset }, { it.first }))
            .mapIndexed { rank, (index, section) ->
                val title = textCandidateTitle(sections, index, snapshot)
                ClipCandidate(
                    id = "text-$rank",
                    title = title,
                    thumbnailUrl = section.imageSrcs.firstOrNull { TextCleaner.isHttpUrl(it) }
                        ?: snapshot.openGraph["og:image"],
                    snippet = snippet(section.text),
                    source = CandidateSource.TEXT,
                    ingredientCount = quantityLines(section.text).takeIf { it >= threshold },
                    totalMinutes = null,
                    payload = CandidatePayload.Text(section),
                )
            }
    }

    private fun isRecipeSection(sections: List<TextSection>, index: Int, threshold: Int): Boolean {
        val section = sections[index]
        if (quantityLines(section.text) >= threshold) return true
        val heading = section.heading ?: return false
        if (!ingredientsWord.containsMatchIn(heading)) return false
        // "Ingredients" heading followed (same text or next few sections) by an instructions word.
        if (instructionsWord.containsMatchIn(section.text)) return true
        return sections.drop(index + 1).take(LOOKAHEAD_SECTIONS).any { next ->
            next.heading?.let { instructionsWord.containsMatchIn(it) } == true
        }
    }

    /**
     * An "Ingredients" h3 belongs to the nearest preceding shallower heading
     * ("Chocolate cake" h2); the page title is the last resort.
     */
    private fun textCandidateTitle(sections: List<TextSection>, index: Int, snapshot: PageSnapshot): String {
        val section = sections[index]
        val heading = section.heading?.trim().orEmpty()
        val generic = heading.isBlank() || ingredientsWord.containsMatchIn(heading) || instructionsWord.containsMatchIn(heading)
        if (!generic) return heading
        for (i in index - 1 downTo 0) {
            val prev = sections[i]
            val h = prev.heading?.trim().orEmpty()
            if (prev.level < section.level && h.isNotBlank() && !ingredientsWord.containsMatchIn(h)) return h
        }
        return snapshot.readable?.title?.takeIf { it.isNotBlank() }
            ?: snapshot.title?.takeIf { it.isNotBlank() }
            ?: heading.ifBlank { DEFAULT_TITLE }
    }

    private fun wholePageCandidate(snapshot: PageSnapshot): ClipCandidate? {
        val readable = snapshot.readable
        val length = readable?.textContent?.length ?: snapshot.sections.sumOf { it.text.length }
        if (length < MIN_WHOLE_PAGE_CHARS) return null
        return ClipCandidate(
            id = WHOLE_PAGE_ID,
            title = readable?.title?.takeIf { it.isNotBlank() } ?: snapshot.title?.takeIf { it.isNotBlank() } ?: DEFAULT_TITLE,
            thumbnailUrl = snapshot.openGraph["og:image"] ?: snapshot.openGraph["twitter:image"],
            snippet = readable?.excerpt?.let { snippet(it) } ?: readable?.textContent?.let { snippet(it) },
            source = CandidateSource.TEXT,
            ingredientCount = null,
            totalMinutes = null,
            payload = CandidatePayload.WholePage,
        )
    }

    // -----------------------------------------------------------------------
    // Render
    // -----------------------------------------------------------------------

    override fun renderLlmInput(candidate: ClipCandidate, snapshot: PageSnapshot, budgetChars: Int): String {
        val header = pageHeader(snapshot)
        val body = when (val payload = candidate.payload) {
            is CandidatePayload.Structured -> renderStructured(payload.node.raw)
            is CandidatePayload.Text -> renderSection(payload.section, snapshot)
            CandidatePayload.WholePage -> renderWholePage(snapshot)
        }
        val available = (budgetChars - header.length - 2).coerceAtLeast(MIN_BODY_CHARS)
        return header + "\n\n" + truncateAtParagraph(body, available)
    }

    private fun pageHeader(snapshot: PageSnapshot): String {
        val host = TextCleaner.host(snapshot.url).ifBlank { snapshot.url }
        val title = TextCleaner.oneLine(snapshot.readable?.title ?: snapshot.title)
        return if (title.isBlank()) "Page: $host" else "Page: $host — $title"
    }

    private fun renderStructured(raw: JSONObject): String = buildString {
        val name = mapper.name(raw)
        if (name.isNotBlank()) appendLine("Name: $name")
        mapper.description(raw)?.let { appendLine("Description: $it") }
        mapper.yieldText(raw)?.let { appendLine("Yield: $it") }
        val prep = mapper.minutes(raw, "prepTime")
        val cook = mapper.minutes(raw, "cookTime")
        val total = mapper.totalMinutes(raw, prep, cook)
        val times = listOfNotNull(
            prep?.let { "prep $it min" },
            cook?.let { "cook $it min" },
            total?.let { "total $it min" },
        )
        if (times.isNotEmpty()) appendLine("Times: ${times.joinToString(", ")}")
        val ingredients = mapper.ingredients(raw)
        if (ingredients.isNotEmpty()) {
            appendLine()
            appendLine("Ingredients:")
            ingredients.forEach { appendLine("- $it") }
        }
        val steps = mapper.instructions(raw)
        if (steps.isNotEmpty()) {
            appendLine()
            appendLine("Instructions:")
            steps.forEachIndexed { i, step -> appendLine("${i + 1}. $step") }
        }
        val images = mapper.imageUrls(raw).size
        if (images > 0) {
            appendLine()
            appendLine("Images: $images")
        }
    }.trim()

    /** The section plus everything nested under it (deeper headings) until the next recipe. */
    private fun renderSection(section: TextSection, snapshot: PageSnapshot): String {
        val sections = snapshot.sections
        val start = sections.indexOfFirst { it === section }.takeIf { it >= 0 } ?: sections.indexOf(section)
        val parts = mutableListOf(sectionText(section))
        if (start >= 0) {
            var added = 0
            for (next in sections.drop(start + 1)) {
                val h = next.heading.orEmpty()
                if (h.isNotBlank() && ingredientsWord.containsMatchIn(h)) break // next recipe
                if (next.level < section.level) break // back up to a parent heading
                if (next.level == section.level && !instructionsWord.containsMatchIn(h) && !notesWord.containsMatchIn(h)) break
                parts += sectionText(next)
                if (++added >= MAX_FOLLOWING_SECTIONS) break
            }
        }
        return parts.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun sectionText(section: TextSection): String {
        val heading = section.heading?.trim().orEmpty()
        val text = TextCleaner.clean(section.text)
        return if (heading.isBlank()) text else "## $heading\n$text"
    }

    private fun renderWholePage(snapshot: PageSnapshot): String {
        val readable = snapshot.readable?.textContent?.let { TextCleaner.clean(it) }
        if (!readable.isNullOrBlank()) return readable
        return snapshot.sections.joinToString("\n\n") { sectionText(it) }
    }

    /**
     * Cuts [text] to at most [limit] characters (marker included), preferring
     * the last blank line, then the last newline, then the last space, in the
     * final 40% of the allowed range.
     */
    internal fun truncateAtParagraph(text: String, limit: Int): String {
        if (text.length <= limit) return text
        val room = (limit - TRUNCATION_MARKER.length).coerceAtLeast(0)
        if (room == 0) return TRUNCATION_MARKER.take(limit)
        val window = text.substring(0, room)
        val floor = (room * 0.6).toInt()
        val cut = listOf("\n\n", "\n", " ")
            .map { window.lastIndexOf(it) }
            .firstOrNull { it >= floor } ?: room
        return window.substring(0, cut).trimEnd() + TRUNCATION_MARKER
    }

    // -----------------------------------------------------------------------
    // Fallback + pictures
    // -----------------------------------------------------------------------

    override fun fallback(candidate: ClipCandidate, snapshot: PageSnapshot): ClippedRecipe? {
        val payload = candidate.payload as? CandidatePayload.Structured ?: return null
        return mapper.map(payload.node, sourceUrl = snapshot.url, id = idFactory(), clippedAt = clock())
    }

    /**
     * A whole-page extraction (no detected candidate, "Extract anyway") is
     * only believed when at least [MIN_WHOLE_PAGE_QUANTITY_LINES] of its
     * ingredients carry a quantity; otherwise the model invented a recipe
     * from a list or category page and the result counts as "no recipe".
     * Detected candidates are always accepted.
     */
    override fun accept(candidate: ClipCandidate, item: ClippedRecipe): Boolean {
        if (candidate.payload !is CandidatePayload.WholePage) return true
        return item.ingredients.count { quantityLine.containsMatchIn(it.trim()) } >= MIN_WHOLE_PAGE_QUANTITY_LINES
    }

    override fun pickPictures(candidate: ClipCandidate, snapshot: PageSnapshot, max: Int): List<String> {
        if (max <= 0) return emptyList()
        val ranked = mutableListOf<String>()

        when (val payload = candidate.payload) {
            is CandidatePayload.Structured -> ranked += mapper.imageUrls(payload.node.raw)
            is CandidatePayload.Text -> ranked += payload.section.imageSrcs
            CandidatePayload.WholePage -> Unit
        }
        ranked += listOfNotNull(
            snapshot.openGraph["og:image:secure_url"],
            snapshot.openGraph["og:image"],
            snapshot.openGraph["twitter:image"],
        )

        val anchorTop = (candidate.payload as? CandidatePayload.Text)?.section?.topOffset
        val images = snapshot.images.filter { TextCleaner.isHttpUrl(it.src) && it.naturalWidth >= MIN_NATURAL_PX && it.naturalHeight >= MIN_NATURAL_PX }
        ranked += images.filter { it.inRecipeCard }.sortedByDescending { it.area }.map { it.src }
        ranked += images.filterNot { it.inRecipeCard }
            .sortedWith(
                if (anchorTop != null) compareBy<PageImage> { abs(it.top - anchorTop) }.thenByDescending { it.area }
                else compareByDescending { it.area },
            )
            .map { it.src }

        return ranked
            .filter { TextCleaner.isHttpUrl(it) }
            .distinctBy { TextCleaner.urlKey(it) }
            .take(max)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private val PageImage.area: Int get() = width * height

    private fun quantityLines(text: String): Int =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.count { quantityLine.containsMatchIn(it) }

    private fun snippet(text: String): String? {
        val flat = TextCleaner.oneLine(text)
        if (flat.isBlank()) return null
        return if (flat.length <= SNIPPET_CHARS) flat else flat.take(SNIPPET_CHARS).trimEnd() + "…"
    }

    private fun hasRecipePath(url: String): Boolean =
        recipePath.containsMatchIn(runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault(url))

    private fun isRecipeType(type: String): Boolean =
        type.equals("Recipe", ignoreCase = true) || type.endsWith("/Recipe", ignoreCase = true) || type.endsWith(":Recipe", ignoreCase = true)

    private fun typesOf(obj: JSONObject): List<String> = when (val t = obj.opt("@type")) {
        is String -> listOf(t)
        is org.json.JSONArray -> (0 until t.length()).mapNotNull { t.optString(it).takeIf(String::isNotBlank) }
        else -> emptyList()
    }

    companion object {
        const val MAX_CANDIDATES = 10
        const val WHOLE_PAGE_ID = "page"
        const val TRUNCATION_MARKER = "\n[…truncated]"
        const val MIN_QUANTITY_LINES = 3
        const val MIN_WHOLE_PAGE_CHARS = 500
        const val MIN_WHOLE_PAGE_QUANTITY_LINES = 2
        private const val LOOKAHEAD_SECTIONS = 3
        private const val MAX_FOLLOWING_SECTIONS = 6
        private const val SNIPPET_CHARS = 100
        private const val MIN_BODY_CHARS = 200
        private const val MIN_NATURAL_PX = 300
        private const val DEFAULT_TITLE = "Recipe"

        /** `2 cups flour`, `½ tsp salt`, `200 g butter`, `1/2 onion`, `3 eggs`. */
        val quantityLine: Regex = Regex(
            "^(?:[-*•–]\\s*)?(?:about\\s+)?[\\d½¼¾⅓⅔⅛]+(?:[\\d/.,\\-–½¼¾⅓⅔⅛]*)\\s*" +
                "(?:(?:g|gr|gram|grams|kg|mg|ml|l|dl|cl|cup|cups|tbsp|tablespoon|tablespoons|tsp|teaspoon|teaspoons|oz|ounce|ounces|lb|lbs|pound|pounds|" +
                "pinch|dash|clove|cloves|slice|slices|can|cans|stick|sticks|bunch|handful|piece|pieces|" +
                "taza|tazas|cucharada|cucharadas|cucharadita|cucharaditas|gramos|litro|litros|" +
                "EL|TL|Tasse|Tassen|Prise|Stück|Becher|cuill[eè]re|cuill[eè]res|tasse|tasses|pincée|gousse|gousses|" +
                "cucchiaio|cucchiai|cucchiaino|cucchiaini|tazza|tazze|pizzico|spicchio|spicchi)\\b\\.?\\s+\\S|\\s+[A-Za-zÀ-ÿ])",
            RegexOption.IGNORE_CASE,
        )
        val ingredientsWord: Regex = Regex("\\b(ingredients?|ingredientes|ingredients|zutaten|ingr[ée]dients|ingredienti)\\b", RegexOption.IGNORE_CASE)
        val instructionsWord: Regex = Regex(
            "\\b(instructions?|method|directions|steps|preparation|procedure|how to make|" +
                "instrucciones|preparaci[oó]n|elaboraci[oó]n|pasos|zubereitung|anleitung|schritte|pr[ée]paration|[ée]tapes|preparazione|procedimento)\\b",
            RegexOption.IGNORE_CASE,
        )
        val notesWord: Regex = Regex("\\b(notes?|tips?|notas|consejos|hinweise|tipps|remarques|conseils|serving|storage)\\b", RegexOption.IGNORE_CASE)
        val recipePath: Regex = Regex("(recipe|recipes|rezept|rezepte|receta|recetas|recepta|receptes|recette|recettes|ricetta|ricette)", RegexOption.IGNORE_CASE)
    }
}
