package com.llmlocal.recipekeeper.eval

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.llmlocal.recipekeeper.domain.ClippedRecipe
import com.llmlocal.recipekeeper.parser.RecipeKeeperDraftParser
import com.llmlocal.recipekeeper.parser.RecipeKeeperParser
import com.llmlocal.recipekeeper.parser.RecipeLineGrammar
import kotlin.math.abs
import kotlin.math.ceil
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Step 4 + 5 of the eval loop. For every fixture with a committed golden
 * under `eval/outputs/<fixture>.txt` (raw model output produced by
 * `tools/eval/run_local_model.sh`), parse it with the production
 * [RecipeKeeperParser] and [RecipeKeeperDraftParser] and check the quality
 * bar from the WS7 brief. Fixtures without a golden are *skipped* via an
 * explicit JUnit assumption, so the class is green in CI without the model.
 *
 * Every row — passed, failed or skipped — also lands in
 * `build/eval/report.md` ([EvalReport]), the data for plan decision #3.
 */
@RunWith(Parameterized::class)
class EvalParserTest(private val fixture: EvalFixture) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<Array<Any>> = EvalFixtures.ALL.map { arrayOf<Any>(it) }

        @JvmStatic
        @AfterClass
        fun writeReport() {
            EvalReport.write()
        }

        private val parser = RecipeKeeperParser()
        private val draftParser = RecipeKeeperDraftParser()
    }

    private fun golden(): String? = EvalFixtures.resourceText("eval/outputs/${fixture.name}.txt")

    private fun timing(): JSONObject? =
        EvalFixtures.resourceText("eval/outputs/${fixture.name}.timing.json")?.let { JSONObject(it) }

    @Test
    fun `golden output parses and meets the quality bar`() {
        val row = EvalReport.row(fixture)
        val golden = golden()
        if (golden == null) {
            row.status = "SKIPPED (no golden)"
            assumeTrue(
                "ASSUMPTION: no golden for ${fixture.name} under eval/outputs/ — run tools/eval/run_local_model.sh to generate it; skipping, not failing",
                false,
            )
            return
        }
        row.goldenChars = golden.length
        row.timing = timing()

        val recipe = parser.parse(golden)
        row.parsed = recipe
        row.outputStartsWithTitle = golden.lineSequence().firstOrNull { it.isNotBlank() }?.let(RecipeLineGrammar::isHeader) == true
        row.unexpectedHeaders = unexpectedHeaders(golden)

        val raw = EvalFixtures.structuredRaw(row.topCandidate)
        val sourceIngredients = raw?.let { EvalFixtures.mapper.ingredients(it) }
        val sourceYield = raw?.let { EvalFixtures.mapper.yieldText(it) }
        val sourceHasTime = raw?.let { EvalFixtures.mapper.totalMinutes(it) != null } ?: false
        row.sourceIngredientCount = sourceIngredients?.size
        row.sourceServings = sourceYield
        row.sourceHasTime = sourceHasTime
        if (recipe != null && sourceIngredients != null) {
            row.jaccard = jaccard(recipe.ingredients, sourceIngredients)
        }

        // Negative fixtures (list pages): record what the model did, assert nothing.
        if (!fixture.expectRecipe) {
            row.status = if (recipe == null) "INFO (no recipe, as expected)" else "INFO (model produced a recipe from a non-recipe page)"
            return
        }

        val checks = mutableListOf<EvalReport.Check>()
        fun check(name: String, pass: Boolean, detail: String) { checks += EvalReport.Check(name, pass, detail) }

        check("parses", recipe != null, if (recipe == null) "RecipeKeeperParser returned null" else "ok")
        if (recipe != null) {
            check("title", recipe.title.isNotBlank(), "'${recipe.title}'")
            check("ingredients ≥ 3", recipe.ingredients.size >= 3, "${recipe.ingredients.size}")
            check("steps ≥ 2", recipe.steps.size >= 2, "${recipe.steps.size}")
            if (sourceIngredients != null) {
                val tolerance = ceil(sourceIngredients.size * 0.2).toInt()
                val diff = abs(recipe.ingredients.size - sourceIngredients.size)
                check("ingredient count ±20% of JSON-LD", diff <= tolerance, "${recipe.ingredients.size} vs ${sourceIngredients.size} (±$tolerance)")
                check("Jaccard ≥ 0.8 vs SchemaOrgRecipeMapper", (row.jaccard ?: 0.0) >= 0.8, "%.2f".format(row.jaccard ?: 0.0))
            }
            if (sourceYield != null) {
                check("servings present (source has yield)", recipe.servings != null, "'${recipe.servings}' vs source '$sourceYield'")
            }
            if (sourceHasTime) {
                val anyTime = recipe.prepMinutes != null || recipe.cookMinutes != null || recipe.totalMinutes != null
                check("time present (source has time)", anyTime, "prep=${recipe.prepMinutes} cook=${recipe.cookMinutes} total=${recipe.totalMinutes}")
            }
            check("no unexpected section headers", row.unexpectedHeaders.isEmpty(), row.unexpectedHeaders.joinToString(" | ").ifEmpty { "none" })
        }
        row.checks = checks
        val failed = checks.filterNot { it.pass }
        val (known, unexpected) = failed.partition { it.name in fixture.knownIssues }
        val resolved = fixture.knownIssues.keys.filter { name -> checks.any { it.name == name && it.pass } }
        row.knownFailures = known.associate { it.name to fixture.knownIssues.getValue(it.name) }
        row.status = when {
            unexpected.isNotEmpty() -> "FAIL (${unexpected.joinToString { it.name }})"
            known.isNotEmpty() -> "KNOWN FAIL (${known.joinToString { it.name }})"
            else -> "OK"
        }

        assertWithMessage("${fixture.name}: ${unexpected.joinToString("; ") { "${it.name}: ${it.detail}" }}")
            .that(unexpected).isEmpty()
        assertWithMessage("${fixture.name}: known issue(s) now pass — remove them from EvalFixtures.knownIssues: $resolved")
            .that(resolved).isEmpty()
    }

    @Test
    fun `draft parser is monotonic over every prefix and converges to the final parse`() {
        val golden = golden()
        assumeTrue("ASSUMPTION: no golden for ${fixture.name}; skipping draft check", golden != null)
        golden!!

        var previous = draftParser.parse("")
        var prefixesChecked = 0
        for (end in 1..golden.length) {
            val draft = draftParser.parse(golden.substring(0, end))
            assertWithMessage("${fixture.name}@$end ingredients shrank").that(draft.ingredients.size).isAtLeast(previous.ingredients.size)
            assertWithMessage("${fixture.name}@$end steps shrank").that(draft.steps.size).isAtLeast(previous.steps.size)
            previous.ingredients.forEachIndexed { i, old ->
                assertWithMessage("${fixture.name}@$end ingredient $i rewritten").that(draft.ingredients[i]).startsWith(old)
            }
            previous.steps.forEachIndexed { i, old ->
                assertWithMessage("${fixture.name}@$end step $i rewritten").that(draft.steps[i]).startsWith(old)
            }
            if (previous.title != null) assertWithMessage("${fixture.name}@$end title changed").that(draft.title).isEqualTo(previous.title)
            previous = draft
            prefixesChecked++
        }
        assertThat(prefixesChecked).isEqualTo(golden.length)

        // With a trailing newline every line is complete: the draft must match the final parser.
        val final = parser.parse(golden) ?: return
        val complete = draftParser.parse(golden + "\n")
        assertThat(complete.ingredients).isEqualTo(final.ingredients)
        assertThat(complete.steps).isEqualTo(final.steps)
        assertThat(complete.title).isEqualTo(final.title.ifBlank { null })
    }

    // ----- helpers -----------------------------------------------------------

    /**
     * Lines that look like a section header but are not in the grammar:
     * a standalone `Label:` line the grammar does not recognise, or any
     * Markdown heading (`# …`). Both are things the prompt forbids.
     */
    private fun unexpectedHeaders(text: String): List<String> {
        val labelLine = Regex("^\\s*[#*_]*[\\p{L}][\\p{L} /&-]{0,40}[*_]*[:：]\\s*$")
        val markdownHeading = Regex("^\\s*#{1,6}\\s+\\S")
        return text.lines().filter { line ->
            (labelLine.matches(line) || markdownHeading.containsMatchIn(line)) && !RecipeLineGrammar.isHeader(line)
        }
    }

    private fun jaccard(a: List<String>, b: List<String>): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() && tb.isEmpty()) return 1.0
        val union = (ta + tb).size
        return if (union == 0) 0.0 else (ta intersect tb).size.toDouble() / union
    }

    private fun tokens(lines: List<String>): Set<String> =
        lines.flatMap { it.lowercase().split(Regex("[^\\p{L}\\p{N}]+")) }.filter { it.isNotBlank() }.toSet()
}

/**
 * Collects one row per fixture from [EvalParserTest] and writes
 * `build/eval/report.md`: per-fixture tier, prompt size, parse result,
 * field coverage, Jaccard vs the deterministic mapper, TTFT / total time
 * from the runner's timing JSON, plus per-tier summary lines and the
 * deterministic-fallback comparison that decision #3 needs.
 */
object EvalReport {

    data class Check(val name: String, val pass: Boolean, val detail: String)

    class Row(val fixture: EvalFixture) {
        val snapshot = EvalFixtures.load(fixture)
        val candidates = EvalFixtures.candidates(snapshot)
        val topCandidate = candidates.firstOrNull()
        val tier = EvalFixtures.tierOf(topCandidate)
        val rendered: String? = topCandidate?.let { EvalFixtures.renderedInput(it, snapshot) }
        val promptChars: Int = rendered?.let { EvalFixtures.fullPrompt(it).length } ?: 0
        val fallback: ClippedRecipe? = topCandidate?.let { EvalFixtures.clipConfig.fallback(it, snapshot) }
        var status: String = "NOT RUN"
        var goldenChars: Int? = null
        var timing: JSONObject? = null
        var parsed: ClippedRecipe? = null
        var outputStartsWithTitle: Boolean? = null
        var unexpectedHeaders: List<String> = emptyList()
        var sourceIngredientCount: Int? = null
        var sourceServings: String? = null
        var sourceHasTime: Boolean = false
        var jaccard: Double? = null
        var checks: List<Check> = emptyList()
        var knownFailures: Map<String, String> = emptyMap()
    }

    private val rows = linkedMapOf<String, Row>()

    @Synchronized
    fun row(fixture: EvalFixture): Row = rows.getOrPut(fixture.name) { Row(fixture) }

    @Synchronized
    fun write() {
        // Make sure every fixture appears, even if its test never ran.
        EvalFixtures.ALL.forEach { row(it) }
        val out = java.io.File(EvalFixtures.buildEvalDir, "report.md")
        out.parentFile.mkdirs()
        out.writeText(render())
        println("eval report → ${out.absolutePath}")
    }

    private fun render(): String = buildString {
        val all = rows.values.toList()
        val firstTiming = all.firstNotNullOfOrNull { it.timing }
        appendLine("# Recipe Keeper eval report")
        appendLine()
        appendLine("Generated ${java.time.ZonedDateTime.now().withNano(0)} by `EvalParserTest` from ${all.size} fixtures.")
        if (firstTiming != null) {
            appendLine(
                "Goldens produced by `${firstTiming.optString("runner")}` — model `${firstTiming.optString("model")}` " +
                    "(sha256 `${firstTiming.optString("modelSha256").take(12)}…`), backend ${firstTiming.optString("backend")}, " +
                    "host `${firstTiming.optString("host")}` ${firstTiming.optString("label")}, " +
                    "sampler ${firstTiming.optJSONObject("sampler")}.",
            )
        } else {
            appendLine("No timing JSON found: goldens have not been generated (run `tools/eval/run_local_model.sh`).")
        }
        appendLine()
        appendLine("## Per fixture")
        appendLine()
        appendLine("| Fixture | Tier | Cands | Prompt chars | Output chars | Status | Title | Servings | Prep/Cook/Total min | Ingr (src) | Steps | Notes | Jaccard | TTFT ms | Total ms | Decode tok/s | Cap hit |")
        appendLine("|---|---|---:|---:|---:|---|---|---|---|---|---:|---|---:|---:|---:|---:|---|")
        all.forEach { r ->
            val p = r.parsed
            val t = r.timing
            val bench = t?.optJSONObject("benchmark")
            appendLine(
                "| ${r.fixture.name} | ${r.tier} | ${r.candidates.size} | ${r.promptChars} | ${r.goldenChars ?: "–"} | ${r.status} | " +
                    "${p?.title?.take(40)?.ifBlank { "(blank)" } ?: "–"} | ${p?.servings ?: "–"} | " +
                    "${p?.let { "${it.prepMinutes ?: "–"}/${it.cookMinutes ?: "–"}/${it.totalMinutes ?: "–"}" } ?: "–"} | " +
                    "${p?.ingredients?.size ?: "–"} (${r.sourceIngredientCount ?: "n/a"}) | ${p?.steps?.size ?: "–"} | ${if (p?.notes != null) "yes" else "–"} | " +
                    "${r.jaccard?.let { "%.2f".format(it) } ?: "–"} | ${t?.opt("ttftMs") ?: "–"} | ${t?.opt("totalMs") ?: "–"} | " +
                    "${bench?.opt("decodeTokensPerS")?.let { "%.1f".format(it.toString().toDouble()) } ?: "–"} | ${t?.opt("maxOutputTokensHit") ?: "–"} |",
            )
        }
        appendLine()
        appendLine("## Per tier")
        appendLine()
        appendLine("| Tier | Fixtures | With golden | Parse ok | All checks ok | Mean prompt chars | Mean TTFT ms | Mean total ms | Mean decode tok/s | Mean Jaccard |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
        all.groupBy { it.tier }.toSortedMap(compareBy { it.ordinal }).forEach { (tier, rs) ->
            val withGolden = rs.filter { it.goldenChars != null }
            val parseOk = withGolden.count { it.parsed != null }
            val checksOk = withGolden.count { it.status == "OK" }
            val meanPrompt = rs.map { it.promptChars }.average()
            val ttft = withGolden.mapNotNull { it.timing?.optDouble("ttftMs")?.takeIf { v -> !v.isNaN() } }
            val total = withGolden.mapNotNull { it.timing?.optDouble("totalMs")?.takeIf { v -> !v.isNaN() } }
            val decode = withGolden.mapNotNull { it.timing?.optJSONObject("benchmark")?.optDouble("decodeTokensPerS")?.takeIf { v -> !v.isNaN() } }
            val jac = withGolden.mapNotNull { it.jaccard }
            appendLine(
                "| $tier | ${rs.size} | ${withGolden.size} | $parseOk | $checksOk | ${"%.0f".format(meanPrompt)} | ${mean(ttft)} | ${mean(total)} | ${mean(decode)} | " +
                    "${jac.takeIf { it.isNotEmpty() }?.let { "%.2f".format(it.average()) } ?: "–"} |",
            )
        }
        appendLine()
        appendLine("## Checks")
        appendLine()
        all.filter { it.checks.isNotEmpty() || it.unexpectedHeaders.isNotEmpty() }.forEach { r ->
            appendLine("### ${r.fixture.name} — ${r.status}")
            appendLine()
            r.checks.forEach { c ->
                val known = r.knownFailures[c.name]
                val mark = when { c.pass -> "✅"; known != null -> "⚠️ known" else -> "❌" }
                appendLine("- $mark ${c.name}: ${c.detail}${known?.let { " — $it" } ?: ""}")
            }
            r.outputStartsWithTitle?.let { appendLine("- ${if (it) "✅" else "⚠️"} output starts with a grammar header (no preamble)") }
            appendLine()
        }
        appendLine("## Decision #3 — model vs deterministic mapper on STRUCTURED fixtures")
        appendLine()
        appendLine("What `RecipeKeeperClipConfig.fallback` (the \"skip the LLM\" path) would have saved, next to what the model produced from the same node. Δ marks where the model result differs from the mapper's.")
        appendLine()
        appendLine("| Fixture | Mapper: ingr / steps / servings / total min | Model: ingr / steps / servings / total min | Δ | Jaccard | Model total ms |")
        appendLine("|---|---|---|---|---:|---:|")
        all.filter { it.tier == ExpectedTier.STRUCTURED }.forEach { r ->
            val f = r.fallback
            val p = r.parsed
            val deltas = if (f != null && p != null) {
                listOfNotNull(
                    "ingredients".takeIf { f.ingredients.size != p.ingredients.size },
                    "steps".takeIf { f.steps.size != p.steps.size },
                    "servings".takeIf { (f.servings ?: "") != (p.servings ?: "") },
                    "time".takeIf { f.totalMinutes != p.totalMinutes },
                ).joinToString(", ").ifEmpty { "none" }
            } else "–"
            appendLine(
                "| ${r.fixture.name} | ${f?.let { "${it.ingredients.size} / ${it.steps.size} / ${it.servings ?: "–"} / ${it.totalMinutes ?: "–"}" } ?: "null"} | " +
                    "${p?.let { "${it.ingredients.size} / ${it.steps.size} / ${it.servings ?: "–"} / ${it.totalMinutes ?: "–"}" } ?: (if (r.goldenChars == null) "no golden" else "parse failed")} | " +
                    "$deltas | ${r.jaccard?.let { "%.2f".format(it) } ?: "–"} | ${r.timing?.opt("totalMs") ?: "–"} |",
            )
        }
        appendLine()
        appendLine("Fixture notes:")
        appendLine()
        all.forEach { appendLine("- `${it.fixture.name}` (${it.fixture.sourceUrl}): ${it.fixture.notes}") }
    }

    private fun mean(values: List<Double>): String = if (values.isEmpty()) "–" else "%.0f".format(values.average())
}
