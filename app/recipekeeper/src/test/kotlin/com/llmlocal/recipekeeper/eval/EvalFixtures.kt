package com.llmlocal.recipekeeper.eval

import com.llmlocal.core.web.JsonLdExtractor
import com.llmlocal.core.web.PageImage
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.core.web.ReadableContent
import com.llmlocal.core.web.TextSection
import com.llmlocal.feature.webclipper.CandidatePayload
import com.llmlocal.feature.webclipper.CandidateSource
import com.llmlocal.feature.webclipper.ClipCandidate
import com.llmlocal.recipekeeper.clip.RecipeKeeperClipConfig
import com.llmlocal.recipekeeper.clip.SchemaOrgRecipeMapper
import com.llmlocal.recipekeeper.prompt.RecipeKeeperPromptBuilder
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The tier `RecipeKeeperClipConfig.detect` is expected to pick for a
 * fixture's top candidate.
 */
enum class ExpectedTier { STRUCTURED, TEXT, WHOLE_PAGE, NONE }

/**
 * One captured real page under `src/test/resources/eval/pages/<name>.json`
 * plus what the standard pipeline is expected to make of it.
 *
 * @property expectRecipe `true` when the page really contains a recipe the
 *   model must extract; `false` for negative fixtures (list pages) where
 *   the eval only records what the model did
 * @property minCandidates / maxCandidates inclusive bounds on `detect().size`
 * @property knownIssues quality checks (by `EvalParserTest` check name) that
 *   are expected to fail on this fixture because of a documented pipeline
 *   bug, mapped to the reason. A known failure is reported, not asserted;
 *   a known issue that unexpectedly *passes* fails the test so the entry
 *   gets removed once the bug is fixed.
 */
data class EvalFixture(
    val name: String,
    val sourceUrl: String,
    val tier: ExpectedTier,
    val minCandidates: Int,
    val maxCandidates: Int = minCandidates,
    val expectRecipe: Boolean = true,
    val notes: String,
    val knownIssues: Map<String, String> = emptyMap(),
)

/**
 * Registry of eval fixtures, the snapshot loader and the prompt assembly
 * shared by `EvalPromptRenderingTest` and `EvalParserTest`.
 *
 * Fixture JSON files are the unmodified output of
 * `core/web/tools/capture-harness/harness.mjs --json` (`{quickScan, capture}`)
 * wrapped with a `_meta` block (URL, fetch date). [load] maps the `capture`
 * object to a [PageSnapshot] the same way `PageSnapshotMapper` (internal to
 * `:core:web`) does; JSON-LD and microdata go through the public
 * [JsonLdExtractor] so Tier A sees exactly the nodes production sees.
 */
object EvalFixtures {

    val ALL: List<EvalFixture> = listOf(
        EvalFixture(
            name = "single_bbcgoodfood_pancakes",
            sourceUrl = "https://www.bbcgoodfood.com/recipes/classic-pancakes",
            tier = ExpectedTier.STRUCTURED,
            minCandidates = 1,
            notes = "JSON-LD single Recipe (3 ld+json blocks, one typed Recipe). Small recipe: 4 ingredients.",
        ),
        EvalFixture(
            name = "graph_pinchofyum_cookies",
            sourceUrl = "https://pinchofyum.com/the-best-soft-chocolate-chip-cookies",
            tier = ExpectedTier.STRUCTURED,
            minCandidates = 1,
            notes = "Yoast @graph with a Recipe node (WordPress / Tasty Recipes). ~9k chars readable.",
        ),
        EvalFixture(
            name = "roundup_guardian_slater",
            sourceUrl = "https://www.theguardian.com/food/2024/dec/09/smoked-salmon-pancakes-mincemeat-mascarpone-fig-ice-cream-nigel-slater-recipes",
            tier = ExpectedTier.STRUCTURED,
            minCandidates = 5,
            notes = "Round-up: five Recipe nodes in one JSON-LD array (Nigel Slater column). Top candidate = first recipe. " +
                "The Guardian writes quantities as floats ('250.0g potatoes').",
            knownIssues = mapOf(
                "Jaccard ≥ 0.8 vs SchemaOrgRecipeMapper" to "production parser bug: RecipeLineGrammar.stripNumber/numbered treat " +
                    "'250.0g potatoes' as numbered item '250.' + '0g potatoes', so every decimal quantity loses its integer part " +
                    "(model output is verbatim; see ws7-report.md for the patch)",
            ),
        ),
        EvalFixture(
            name = "howto_recipetineats_tikka",
            sourceUrl = "https://www.recipetineats.com/chicken-tikka-masala/",
            tier = ExpectedTier.STRUCTURED,
            minCandidates = 1,
            notes = "WPRM @graph Recipe whose recipeInstructions are HowToSection groups; 30 ingredients.",
        ),
        EvalFixture(
            name = "plain_joyofbaking_pancakes",
            sourceUrl = "https://www.joyofbaking.com/breakfast/Pancakes.html",
            tier = ExpectedTier.TEXT,
            minCandidates = 1,
            maxCandidates = 3,
            notes = "Plain-text blog, no structured data (quickScan 0). Ingredients live under a 'Pancake Recipe:' h2 with " +
                "quantity lines; the method is prose in the h1 section. Tier B by quantity lines.",
            knownIssues = mapOf(
                "steps ≥ 2" to "pipeline gap: renderSection only pulls sections *after* the candidate, so the method prose in the " +
                    "preceding h1 section never reaches the model and it emits an empty 'Steps:' (see ws7-report.md)",
            ),
        ),
        EvalFixture(
            name = "plain_cfe_banana_bread",
            sourceUrl = "https://www.cookingforengineers.com/recipe/108/Banana-Nut-Bread",
            tier = ExpectedTier.TEXT,
            minCandidates = 1,
            maxCandidates = 3,
            notes = "Plain-text, table-based 2000s layout, no structured data. One h1 section (capped at 6000 chars) " +
                "holding prose + quantity lines; '/recipe/' in the URL relaxes the quantity-line threshold.",
        ),
        EvalFixture(
            name = "nonen_marmiton_crepes",
            sourceUrl = "https://www.marmiton.org/recettes/recette_crepes-faciles_12372.aspx",
            tier = ExpectedTier.STRUCTURED,
            minCandidates = 1,
            notes = "French page, @graph Recipe. Checks the model keeps French ingredient lines verbatim.",
        ),
        EvalFixture(
            name = "list_pinchofyum_recipes",
            sourceUrl = "https://pinchofyum.com/recipes",
            tier = ExpectedTier.WHOLE_PAGE,
            minCandidates = 1,
            expectRecipe = false,
            notes = "Category/list page with no recipe. Readable text ≥ 500 chars so a single WholePage candidate is offered.",
        ),
        EvalFixture(
            name = "long_theperfectloaf_sourdough",
            sourceUrl = "https://www.theperfectloaf.com/beginners-sourdough-bread/",
            tier = ExpectedTier.STRUCTURED,
            minCandidates = 1,
            notes = "Very long page (~31k chars readable) with a WPRM Recipe node; structured rendering must stay compact.",
        ),
        EvalFixture(
            name = "micro_nigella_banana_bread",
            sourceUrl = "https://www.nigella.com/recipes/banana-bread",
            tier = ExpectedTier.STRUCTURED,
            minCandidates = 1,
            notes = "Microdata (itemtype schema.org/Recipe) converted to pseudo-JSON-LD by capture.js; ~23k chars readable.",
        ),
    )

    fun byName(name: String): EvalFixture = ALL.first { it.name == name }

    /** Production config with production defaults (real `IsoDuration`). */
    val clipConfig = RecipeKeeperClipConfig()

    val mapper = SchemaOrgRecipeMapper()

    val promptBuilder = RecipeKeeperPromptBuilder()

    /** Gradle runs unit tests with the module directory as the working dir. */
    val moduleDir: File = File(System.getProperty("user.dir"))

    val buildEvalDir: File get() = File(moduleDir, "build/eval")

    val promptsDir: File get() = File(buildEvalDir, "prompts")

    // ---------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------

    fun resourceText(path: String): String? =
        Thread.currentThread().contextClassLoader?.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }

    fun load(fixture: EvalFixture): PageSnapshot = load(fixture.name)

    fun load(name: String): PageSnapshot {
        val text = checkNotNull(resourceText("eval/pages/$name.json")) { "missing fixture eval/pages/$name.json" }
        val doc = JSONObject(text)
        val capture = doc.getJSONObject("capture")
        return fromCapture(capture, capturedAt = 1_760_000_000_000L)
    }

    /** `_meta` block of a fixture file (source URL, fetch date, HTML size). */
    fun meta(name: String): JSONObject {
        val text = checkNotNull(resourceText("eval/pages/$name.json"))
        return JSONObject(text).optJSONObject("_meta") ?: JSONObject()
    }

    /** Names of every JSON fixture under `eval/pages` on the classpath. */
    fun fixtureFilesOnClasspath(): Set<String> {
        val url = Thread.currentThread().contextClassLoader?.getResource("eval/pages") ?: return emptySet()
        val dir = File(url.toURI())
        return dir.listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension }.toSet()
    }

    /** Mirrors `PageSnapshotMapper.fromJson` (internal in `:core:web`). */
    fun fromCapture(json: JSONObject, capturedAt: Long): PageSnapshot {
        val jsonLdBlocks = json.optJSONArray("jsonLd").toStringList()
        val microdataBlocks = json.optJSONArray("microdata").toStringList()
        return PageSnapshot(
            url = json.optString("url"),
            title = json.optStringOrNull("title"),
            siteName = json.optStringOrNull("siteName"),
            jsonLd = JsonLdExtractor.fromScriptBlocks(jsonLdBlocks + microdataBlocks),
            readable = json.optJSONObject("readable")?.let { r ->
                ReadableContent(
                    title = r.optStringOrNull("title"),
                    byline = r.optStringOrNull("byline"),
                    excerpt = r.optStringOrNull("excerpt"),
                    textContent = r.optString("textContent"),
                    length = r.optInt("length", r.optString("textContent").length),
                )
            },
            sections = json.optJSONArray("sections").toObjectList().map { s ->
                TextSection(
                    heading = s.optStringOrNull("heading"),
                    level = s.optInt("level", 0),
                    text = s.optString("text"),
                    imageSrcs = s.optJSONArray("imageSrcs").toStringList(),
                    topOffset = s.optInt("topOffset", 0),
                )
            },
            images = json.optJSONArray("images").toObjectList().map { i ->
                PageImage(
                    src = i.optString("src"),
                    alt = i.optStringOrNull("alt"),
                    width = i.optInt("width", 0),
                    height = i.optInt("height", 0),
                    naturalWidth = i.optInt("naturalWidth", 0),
                    naturalHeight = i.optInt("naturalHeight", 0),
                    top = i.optInt("top", 0),
                    inRecipeCard = i.optBoolean("inRecipeCard", false),
                )
            },
            openGraph = json.optJSONObject("og").toStringMap(),
            capturedAt = capturedAt,
        )
    }

    // ---------------------------------------------------------------------
    // Pipeline helpers (what the app does, step by step)
    // ---------------------------------------------------------------------

    fun tierOf(candidate: ClipCandidate?): ExpectedTier = when (candidate?.payload) {
        null -> ExpectedTier.NONE
        is CandidatePayload.Structured -> ExpectedTier.STRUCTURED
        is CandidatePayload.Text -> ExpectedTier.TEXT
        CandidatePayload.WholePage -> ExpectedTier.WHOLE_PAGE
    }

    fun candidates(snapshot: PageSnapshot): List<ClipCandidate> = clipConfig.detect(snapshot)

    fun topCandidate(snapshot: PageSnapshot): ClipCandidate? = candidates(snapshot).firstOrNull()

    /** `renderLlmInput` for the top candidate with the production budget. */
    fun renderedInput(candidate: ClipCandidate, snapshot: PageSnapshot): String =
        clipConfig.renderLlmInput(candidate, snapshot, clipConfig.budgetChars)

    /**
     * The exact string `LlmTaskRepositoryImpl.generateStream` sends to the
     * engine: `systemPrompt() + "\n\n" + userPrompt(listOf(renderedInput))`,
     * as one user turn (no separate system instruction).
     */
    fun fullPrompt(renderedInput: String): String = buildString {
        append(promptBuilder.systemPrompt())
        append("\n\n")
        append(promptBuilder.userPrompt(listOf(renderedInput)))
    }

    /** The JSON-LD node behind a STRUCTURED candidate, or null. */
    fun structuredRaw(candidate: ClipCandidate?): JSONObject? =
        (candidate?.payload as? CandidatePayload.Structured)?.node?.raw

    fun CandidateSource.label(): String = name

    // ---------------------------------------------------------------------
    // org.json helpers (same semantics as PageSnapshotMapper)
    // ---------------------------------------------------------------------

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).ifBlank { null }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val out = LinkedHashMap<String, String>()
        keys().forEach { k ->
            val v = opt(k)
            if (v != null && v != JSONObject.NULL) out[k] = v.toString()
        }
        return out
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> opt(i)?.takeIf { it != JSONObject.NULL }?.toString() }
    }

    private fun JSONArray?.toObjectList(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
