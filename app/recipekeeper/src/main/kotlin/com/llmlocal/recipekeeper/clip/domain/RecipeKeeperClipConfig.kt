package com.llmlocal.recipekeeper.clip.domain

import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.web.IsoDuration
import com.llmlocal.core.web.JsonLdNode
import com.llmlocal.core.web.PageImage
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.core.web.TextSection
import com.llmlocal.feature.webclipper.domain.CandidatePayload
import com.llmlocal.feature.webclipper.domain.CandidateSource
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.feature.webclipper.domain.PicturePick
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import org.json.JSONObject
import kotlin.math.abs

/**
 * Recipe Keeper's [WebClipConfig]: how a captured page becomes recipe
 * candidates, LLM input, a deterministic fallback and a picture list
 * (plan §3.2, §3.3, §3.6).
 *
 * **Detect.** Tier A: every JSON-LD node typed `Recipe` (or whose
 * `mainEntity` is one) **that carries ingredients or instructions** is a
 * STRUCTURED candidate; the ingredient-less stubs collection pages emit
 * per card (name / description / image / cookTime) are skipped, since the
 * model would otherwise invent the missing lines (WS10 bug 3). Tier B (only when Tier A
 * finds nothing): a heading section is a TEXT candidate when its text has
 * at least [MIN_QUANTITY_LINES] quantity-unit lines, or its heading is an
 * "ingredients" word and an "instructions" word follows within the next
 * sections. A recipe-ish URL path relaxes the line threshold by one, but only when that
 * yields a single section and the path is not list-style (collection, category, tag, search, gallery, round-up). When
 * neither tier finds anything the list is **empty**: the feature then shows
 * its "No recipe found" sheet, whose "Extract anyway" builds the `WholePage`
 * candidate itself (`wholePageCandidate(snapshot)`); `detect` never offers
 * it, otherwise list and category pages would skip the sheet and go
 * straight to a 9,000-char extraction (WS10 bug 2). Ranking: STRUCTURED
 * first in document order, then TEXT by heading level, then position.
 * Capped at 10.
 *
 * **Render.** STRUCTURED → compact field rendering (`Name / Description /
 * Yield / Times / Ingredients / Instructions / Images: n`). TEXT → the
 * section plus the sections nested under it (so "Ingredients" h3 pulls in
 * the sibling "Method" h3); when none of that mentions instructions, the
 * nearest *preceding* section with at least [MIN_PRECEDING_PROSE_CHARS] of
 * prose is prepended (blogs that put the method above the ingredient
 * list), trimmed from its start so the candidate itself stays whole.
 * WholePage → `readable.textContent`. All three start with
 * `Page: <host> — <title>` and are trimmed to `budgetChars` at a paragraph
 * boundary with a `[…truncated]` marker.
 *
 * @param untitledTitle title for recipes with no name (user-visible; from resources)
 * @param durationParser ISO-8601/free-text → minutes; `IsoDuration.toMinutes`
 *   (WS2) in production, a lambda in tests
 * @param idFactory id for saved recipes (the fallback path); UUID by default
 * @param clock epoch millis for `clippedAt`
 */
class RecipeKeeperClipConfig(
    private val untitledTitle: String,
    durationParser: (String?) -> Int? = IsoDuration::toMinutes,
    private val mapper: SchemaOrgRecipeMapper = SchemaOrgRecipeMapper(untitledTitle, durationParser),
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

        return emptyList()
    }

    private fun detectStructured(snapshot: PageSnapshot): List<ClipCandidate> {
        val seen = HashSet<String>()
        return snapshot.jsonLd
            .mapNotNull { node -> recipeNode(node) }
            .filter { node ->
                val raw = node.raw
                val ingredients = mapper.ingredients(raw).size
                val steps = mapper.instructions(raw).size
                if (ingredients == 0 && steps == 0) return@filter false // collection-card stub
                seen.add(mapper.name(raw).lowercase() + "|" + ingredients + "|" + steps)
            }
            .mapIndexed { index, node ->
                val raw = node.raw
                val ingredients = mapper.ingredients(raw)
                val title = mapper.name(raw).ifBlank { snapshot.title?.trim().orEmpty() }.ifBlank { untitledTitle }
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

        // Strict threshold first. A recipe-ish path may relax it by one, but only
        // when that finds exactly one section: a real recipe page has one
        // ingredient block, a collection page has one per card (WS12 A), and a
        // list-style path never relaxes.
        var threshold = MIN_QUANTITY_LINES
        var picked = pickSections(sections, threshold)
        if (picked.isEmpty() && hasRecipePath(snapshot.url) && !hasListPath(snapshot.url)) {
            val relaxed = pickSections(sections, MIN_QUANTITY_LINES - 1)
            if (relaxed.size == 1) {
                picked = relaxed
                threshold = MIN_QUANTITY_LINES - 1
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

    private fun pickSections(sections: List<TextSection>, threshold: Int): List<Pair<Int, TextSection>> {
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
            ?: heading.ifBlank { untitledTitle }
    }

    // -----------------------------------------------------------------------
    // Render
    // -----------------------------------------------------------------------

    override fun renderLlmInput(candidate: ClipCandidate, snapshot: PageSnapshot, budgetChars: Int): String {
        val header = pageHeader(snapshot)
        val available = (budgetChars - header.length - 2).coerceAtLeast(MIN_BODY_CHARS)
        val body = when (val payload = candidate.payload) {
            is CandidatePayload.Structured -> renderStructured(payload.node.raw)
            is CandidatePayload.Text -> renderSection(payload.section, snapshot, available)
            CandidatePayload.WholePage -> renderWholePage(snapshot)
        }
        val trimmed = truncateAtParagraph(body, available)
        // [accept] has no snapshot: remember what the model was shown for a TEXT candidate.
        if (candidate.payload is CandidatePayload.Text) renderedText[candidate.id] = trimmed
        return header + "\n\n" + trimmed
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

    /**
     * The section plus everything nested under it (deeper headings) until
     * the next recipe. When neither the section nor what follows mentions
     * instructions, the method is probably in the prose *above* the
     * ingredient list: the nearest preceding section with enough prose is
     * prepended, cut from its start at a paragraph boundary so the whole
     * thing fits [budget].
     */
    private fun renderSection(section: TextSection, snapshot: PageSnapshot, budget: Int): String {
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
        val body = parts.filter { it.isNotBlank() }.joinToString("\n\n")
        if (start <= 0 || instructionsWord.containsMatchIn(body)) return body

        val preceding = precedingProse(sections, start) ?: return body
        val room = budget - body.length - 2
        if (room < MIN_PRECEDING_PROSE_CHARS) return body
        return trimHeadAtParagraph(preceding, room) + "\n\n" + body
    }

    /**
     * Rendered text of the nearest section before [index] whose non-quantity
     * lines add up to at least [MIN_PRECEDING_PROSE_CHARS]; the search stops
     * at another recipe's ingredient heading.
     */
    private fun precedingProse(sections: List<TextSection>, index: Int): String? {
        for (i in index - 1 downTo 0) {
            val prev = sections[i]
            val heading = prev.heading.orEmpty()
            if (heading.isNotBlank() && ingredientsWord.containsMatchIn(heading)) return null
            val text = TextCleaner.clean(prev.text)
            val prose = text.lineSequence().filter { it.isNotBlank() && !quantityLine.containsMatchIn(it.trim()) }.sumOf { it.length }
            if (prose >= MIN_PRECEDING_PROSE_CHARS) return sectionText(prev)
        }
        return null
    }

    /**
     * Keeps the *end* of [text] within [limit] characters (marker included),
     * cutting at the first blank line, then newline, then space, in the
     * first 40% of the kept window; a `## heading` first line survives.
     */
    internal fun trimHeadAtParagraph(text: String, limit: Int): String {
        if (text.length <= limit) return text
        val heading = text.lineSequence().first().takeIf { it.startsWith("## ") }?.let { it + "\n" }.orEmpty()
        val body = text.drop(heading.length)
        val room = (limit - heading.length - HEAD_MARKER.length).coerceAtLeast(0)
        if (room == 0) return (heading + HEAD_MARKER).take(limit)
        val window = body.takeLast(room)
        val ceiling = (room * 0.4).toInt()
        val cut = listOf("\n\n", "\n", " ")
            .map { window.indexOf(it) }
            .firstOrNull { it in 0..ceiling } ?: 0
        return heading + HEAD_MARKER + window.substring(cut).trimStart()
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
        val recipe = mapper.map(payload.node, sourceUrl = snapshot.url, id = idFactory(), clippedAt = clock()) ?: return null
        // WPRM / Tasty / Mediavine keep Notes outside the JSON-LD (WS-sites defect 4).
        // On a round-up the page's notes could belong to any recipe, so skip them.
        val notes = if (recipe.notes == null && detectStructured(snapshot).size == 1) pageNotes(snapshot) else recipe.notes
        return recipe.copy(notes = notes)
    }

    /** First heading section matching [notesWord], cleaned and cut to [MAX_NOTES_CHARS] at a word. */
    private fun pageNotes(snapshot: PageSnapshot): String? {
        val text = snapshot.sections
            .firstOrNull { s -> s.heading?.let { notesWord.containsMatchIn(it) } == true && TextCleaner.clean(s.text).isNotBlank() }
            ?.let { TextCleaner.clean(it.text) }
            ?: return null
        if (text.length <= MAX_NOTES_CHARS) return text
        val cut = text.take(MAX_NOTES_CHARS)
        return cut.substring(0, cut.lastIndexOf(' ').takeIf { it > MAX_NOTES_CHARS / 2 } ?: MAX_NOTES_CHARS).trimEnd() + "…"
    }

    /**
     * Plan decision #3 switch. The WS7 eval showed the model reproducing the
     * mapper verbatim on 6/7 well-formed JSON-LD pages at 11–32 s each, so
     * skipping it for complete mappings is the recommended default; it is
     * Switched **on** 2026-10-19 (plan decision #3): the WS7/WS8 eval showed the
     * model reproduces the deterministic mapping on 6 of 7 structured pages at a
     * cost of 10–40 s per clip. Thin mappings and text pages still run the model.
     */
    override val skipModelWhenStructuredComplete: Boolean = true

    /**
     * The eval's quality bar: enough ingredients, more than one step, steps
     * with substance (mean length ≥ [MIN_MEAN_STEP_CHARS] or ≥
     * [MIN_TOTAL_STEP_CHARS] in all — a one-blob or paraphrased method fails)
     * and no dash-joined ingredient line (the list was one string).
     */
    override fun isComplete(item: ClippedRecipe): Boolean {
        if (item.ingredients.size < MIN_COMPLETE_INGREDIENTS || item.steps.size < MIN_COMPLETE_STEPS) return false
        val total = item.steps.sumOf { it.length }
        if (total / item.steps.size < MIN_MEAN_STEP_CHARS && total < MIN_TOTAL_STEP_CHARS) return false
        return item.ingredients.none { joinedSeparator.findAll(it).count() >= MIN_JOINED_SEPARATORS }
    }

    /**
     * Quality floor on a parsed model result.
     *
     *  - Whole-page extraction ("Extract anyway", no detected candidate) is
     *    only believed when at least [MIN_ACCEPT_QUANTITY_LINES] of its
     *    ingredients carry a quantity; otherwise the model invented a recipe
     *    from a list or category page and the result counts as "no recipe".
     *  - Detected (STRUCTURED / TEXT) candidates need either that many
     *    quantity-bearing ingredient lines **or** at least
     *    [MIN_COMPLETE_STEPS] steps. A two-line "recipe" filled in from a
     *    collection-card stub (WS10 bug 3) therefore becomes a parse
     *    failure: STRUCTURED falls back to [SchemaOrgRecipeMapper] (also
     *    empty for a stub → the parse-failed screen), TEXT shows it directly.
     */
    override fun accept(candidate: ClipCandidate, item: ClippedRecipe): Boolean {
        val quantityLines = item.ingredients.count { quantityLine.containsMatchIn(it.trim()) }
        return when (val payload = candidate.payload) {
            CandidatePayload.WholePage -> quantityLines >= MIN_ACCEPT_QUANTITY_LINES
            is CandidatePayload.Text ->
                (quantityLines >= MIN_ACCEPT_QUANTITY_LINES || item.steps.size >= MIN_COMPLETE_STEPS) &&
                    stepsCoverMethod(renderedText[candidate.id] ?: sectionText(payload.section), item.steps)
            else -> quantityLines >= MIN_ACCEPT_QUANTITY_LINES || item.steps.size >= MIN_COMPLETE_STEPS
        }
    }

    /** Rendered body of the last TEXT candidate per id, for [accept]'s coverage check. */
    private val renderedText = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Smitten-style failure: the page has long method paragraphs and the model
     * answered with a two-line paraphrase. The method is the text after the
     * last instructions heading (else after the last quantity line) up to a
     * notes heading; the parsed [steps] must reuse ≥ [MIN_METHOD_COVERAGE] of
     * its words and number at least half its paragraphs. Without a clear
     * method (fewer than two paragraphs) there is nothing to compare, so the
     * result is believed.
     */
    private fun stepsCoverMethod(rendered: String, steps: List<String>): Boolean {
        val paragraphs = methodParagraphs(rendered)
        if (paragraphs.size < 2) return true
        val source = paragraphs.flatMap(::words).toSet()
        if (source.isEmpty()) return true
        val parsed = steps.flatMap(::words).toSet()
        val coverage = source.count { it in parsed }.toDouble() / source.size
        return coverage >= MIN_METHOD_COVERAGE && steps.size * 2 >= paragraphs.size
    }

    private fun methodParagraphs(rendered: String): List<String> {
        val lines = rendered.lines()
        val headingIdx = lines.indexOfLast { it.startsWith("## ") && instructionsWord.containsMatchIn(it) }
        val method = mutableListOf<String>()
        if (headingIdx >= 0) {
            for (line in lines.drop(headingIdx + 1)) {
                if (line.startsWith("## ") && notesWord.containsMatchIn(line)) break
                val trimmed = line.trim()
                if (isParagraph(trimmed)) method += trimmed
            }
            return method
        }
        // No instructions heading (blog prose): only the run of paragraphs right
        // after the ingredient lines counts; the first short line (related links,
        // comments header, …) ends it, so page furniture is never "the method".
        val start = lines.indexOfLast { quantityLine.containsMatchIn(it.trim()) } + 1
        for (line in lines.drop(start)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (!isParagraph(trimmed)) break
            method += trimmed
        }
        return method
    }

    private fun isParagraph(line: String): Boolean =
        line.length >= MIN_PARAGRAPH_CHARS && !line.startsWith("## ") && !quantityLine.containsMatchIn(line)

    private fun words(text: String): List<String> =
        wordRegex.findAll(text.lowercase()).map { it.value }.filter { it.length >= 3 }.toList()

    override fun pickPictures(candidate: ClipCandidate, snapshot: PageSnapshot, max: Int): List<String> =
        rankPictures(candidate, snapshot, max).map { it.url }

    /**
     * Ranking: the candidate's own images (JSON-LD `image` / the section's
     * inline images), then `og:image` / `twitter:image`, then recipe-card
     * images by area, then the rest of the page by proximity (TEXT) or area.
     *
     * Preferred (= pre-checked on the extraction screen, WS10 bug 4):
     *  - STRUCTURED: the node's own images; `og:image` too when the page
     *    holds a single recipe node (it is then that recipe's hero). Page
     *    images are offered unchecked. On a round-up (more than one recipe
     *    node) the og / page pool is skipped altogether — every one of those
     *    pictures belongs to some other recipe on the page.
     *  - TEXT / WholePage: everything, as before — there is no structured
     *    ownership to go by, and proximity ranking already favours the
     *    section's neighbourhood.
     */
    override fun rankPictures(candidate: ClipCandidate, snapshot: PageSnapshot, max: Int): List<PicturePick> {
        if (max <= 0) return emptyList()
        val payload = candidate.payload
        val structured = payload is CandidatePayload.Structured
        val roundUp = structured && detectStructured(snapshot).size > 1
        val poolPreferred = !structured

        val own: List<String> = when (payload) {
            is CandidatePayload.Structured -> mapper.imageUrls(payload.node.raw)
            is CandidatePayload.Text -> payload.section.imageSrcs
            CandidatePayload.WholePage -> emptyList()
        }
        val og = listOfNotNull(
            snapshot.openGraph["og:image:secure_url"],
            snapshot.openGraph["og:image"],
            snapshot.openGraph["twitter:image"],
        )
        val anchorTop = (payload as? CandidatePayload.Text)?.section?.topOffset
        val images = snapshot.images.filter { TextCleaner.isHttpUrl(it.src) && it.naturalWidth >= MIN_NATURAL_PX && it.naturalHeight >= MIN_NATURAL_PX }
        val pageImages = images.filter { it.inRecipeCard }.sortedByDescending { it.area }.map { it.src } +
            images.filterNot { it.inRecipeCard }
                .sortedWith(
                    if (anchorTop != null) compareBy<PageImage> { abs(it.top - anchorTop) }.thenByDescending { it.area }
                    else compareByDescending { it.area },
                )
                .map { it.src }

        val ranked = mutableListOf<PicturePick>()
        own.forEach { ranked += PicturePick(it, preferred = true) }
        if (!roundUp) {
            og.forEach { ranked += PicturePick(it, preferred = poolPreferred || structured) }
            pageImages.forEach { ranked += PicturePick(it, preferred = poolPreferred) }
        }
        val naturalWidth = snapshot.images.associate { it.src to it.naturalWidth }
        return ranked
            .filter { TextCleaner.isHttpUrl(it.url) }
            .groupBy { PictureKeys.key(it.url) }
            .values
            .map { variants ->
                // Keep the first (best-ranked) slot's flag, but the largest variant's URL.
                val largest = variants.maxByOrNull { sizeOf(it.url, naturalWidth) }!!
                variants.first().copy(url = largest.url)
            }
            .take(max)
    }

    /** Page-measured width, else the width the URL declares, else "unsuffixed original" = biggest. */
    private fun sizeOf(url: String, natural: Map<String, Int>): Int =
        natural[url] ?: PictureKeys.declaredWidth(url) ?: Int.MAX_VALUE

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

    private fun hasListPath(url: String): Boolean =
        listPath.containsMatchIn(runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault(url))

    private fun isRecipeType(type: String): Boolean =
        type.equals("Recipe", ignoreCase = true) || type.endsWith("/Recipe", ignoreCase = true) || type.endsWith(":Recipe", ignoreCase = true)

    private fun typesOf(obj: JSONObject): List<String> = when (val t = obj.opt("@type")) {
        is String -> listOf(t)
        is org.json.JSONArray -> (0 until t.length()).mapNotNull { t.optString(it).takeIf(String::isNotBlank) }
        else -> emptyList()
    }

    companion object {
        const val MAX_CANDIDATES = 10
        const val TRUNCATION_MARKER = "\n[…truncated]"
        const val HEAD_MARKER = "[…]\n"
        const val MIN_PRECEDING_PROSE_CHARS = 300
        const val MIN_QUANTITY_LINES = 3
        /** Quantity-bearing ingredient lines a model result needs (see [accept]). */
        const val MIN_ACCEPT_QUANTITY_LINES = 2
        const val MIN_COMPLETE_INGREDIENTS = 3
        const val MIN_COMPLETE_STEPS = 2
        const val MIN_MEAN_STEP_CHARS = 25
        const val MIN_TOTAL_STEP_CHARS = 120
        private const val MIN_JOINED_SEPARATORS = 3
        private val joinedSeparator = Regex("\\s[-–]\\s")
        private const val LOOKAHEAD_SECTIONS = 3
        private const val MAX_FOLLOWING_SECTIONS = 6
        private const val SNIPPET_CHARS = 100
        private const val MIN_BODY_CHARS = 200
        private const val MIN_NATURAL_PX = 300
        private const val MAX_NOTES_CHARS = 600
        private const val MIN_METHOD_COVERAGE = 0.6
        private const val MIN_PARAGRAPH_CHARS = 40
        private val wordRegex = Regex("\\p{L}+")

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
        /** List-style URL paths: collections, categories, tags, search, galleries, round-ups. */
        val listPath: Regex = Regex("(?:^|[/_\\-])(?:collections?|categor(?:y|ies)|tags?|search|galleries|gallery|roundups?)(?:[/_\\-.]|$)", RegexOption.IGNORE_CASE)
        val recipePath: Regex = Regex("(recipe|recipes|rezept|rezepte|receta|recetas|recepta|receptes|recette|recettes|ricetta|ricette)", RegexOption.IGNORE_CASE)
    }
}
