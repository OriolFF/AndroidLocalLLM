package com.llmlocal.recipekeeper.clip

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.web.JsonLdNode
import com.llmlocal.feature.webclipper.CandidatePayload
import com.llmlocal.feature.webclipper.CandidateSource
import com.llmlocal.recipekeeper.domain.ClippedRecipe
import com.llmlocal.recipekeeper.testing.image
import com.llmlocal.recipekeeper.testing.loadJsonLd
import com.llmlocal.recipekeeper.testing.readable
import com.llmlocal.recipekeeper.testing.section
import com.llmlocal.recipekeeper.testing.snapshot
import com.llmlocal.recipekeeper.testing.testDurationParser
import org.json.JSONObject
import org.junit.Test

class RecipeKeeperClipConfigTest {

    private val config = RecipeKeeperClipConfig(
        durationParser = testDurationParser,
        idFactory = { "fixed-id" },
        clock = { 1_234L },
    )

    // -----------------------------------------------------------------------
    // detect
    // -----------------------------------------------------------------------

    @Test
    fun `structured page yields one STRUCTURED candidate per Recipe node with meta`() {
        val page = snapshot(jsonLd = listOf(loadJsonLd("single_recipe"), website(), loadJsonLd("howto_sections")))

        val candidates = config.detect(page)

        assertThat(candidates).hasSize(2)
        assertThat(candidates.map { it.source }).containsExactly(CandidateSource.STRUCTURED, CandidateSource.STRUCTURED)
        val first = candidates[0]
        assertThat(first.id).isEqualTo("structured-0")
        assertThat(first.title).isEqualTo("Classic Banana Bread")
        assertThat(first.thumbnailUrl).isEqualTo("https://cdn.example.com/banana-bread.jpg")
        assertThat(first.ingredientCount).isEqualTo(8)
        assertThat(first.totalMinutes).isEqualTo(75)
        assertThat(first.snippet).startsWith("Moist & easy")
        assertThat(first.payload).isInstanceOf(CandidatePayload.Structured::class.java)
        assertThat(candidates[1].title).isEqualTo("Lasagne al forno")
        assertThat(candidates[1].totalMinutes).isEqualTo(150)
    }

    @Test
    fun `WebPage mainEntity Recipe is detected and duplicates collapse`() {
        val recipe = loadJsonLd("legacy_text_array")
        val webPage = JsonLdNode(listOf("WebPage"), JSONObject().put("@type", "WebPage").put("mainEntity", recipe.raw))

        val candidates = config.detect(snapshot(jsonLd = listOf(webPage, recipe)))

        assertThat(candidates).hasSize(1)
        assertThat(candidates.single().title).isEqualTo("Quick Guacamole")
    }

    @Test
    fun `structured candidates are capped at 10`() {
        val nodes = (1..14).map { i ->
            val raw = JSONObject(loadJsonLd("legacy_text_array").raw.toString()).put("name", "Recipe $i")
            JsonLdNode(listOf("Recipe"), raw)
        }
        assertThat(config.detect(snapshot(jsonLd = nodes))).hasSize(RecipeKeeperClipConfig.MAX_CANDIDATES)
    }

    @Test
    fun `text page with quantity lines yields TEXT candidates titled by the parent heading, ranked by level`() {
        val page = snapshot(
            url = "https://blog.example.com/2024/05/sunday-bakes",
            sections = listOf(
                section("Sunday bakes", 1, "Two recipes I love.", top = 0),
                section("Lemon drizzle cake", 2, "A classic.", top = 400),
                section("Ingredients", 3, LEMON_INGREDIENTS, top = 500, images = listOf("https://img.example.com/lemon.jpg")),
                section("Method", 3, "Cream the butter and sugar.\nBeat in the eggs.\nBake for 45 minutes.", top = 900),
                section("Soda bread", 2, SODA_INGREDIENTS, top = 1500),
                section("About me", 2, "I bake on Sundays.", top = 2500),
            ),
        )

        val candidates = config.detect(page)

        assertThat(candidates.map { it.source }.toSet()).containsExactly(CandidateSource.TEXT)
        assertThat(candidates.map { it.title }).containsExactly("Soda bread", "Lemon drizzle cake").inOrder() // h2 before h3
        val lemon = candidates.first { it.title == "Lemon drizzle cake" }
        assertThat(lemon.thumbnailUrl).isEqualTo("https://img.example.com/lemon.jpg")
        assertThat(lemon.ingredientCount).isEqualTo(4)
        assertThat((lemon.payload as CandidatePayload.Text).section.heading).isEqualTo("Ingredients")
        assertThat(candidates.map { it.id }).containsExactly("text-0", "text-1").inOrder()
    }

    @Test
    fun `ingredients heading followed by a method heading qualifies without quantity lines`() {
        val page = snapshot(
            url = "https://example.com/post",
            sections = listOf(
                section("Grandma's stew", 1, "", top = 0),
                section("Ingredients", 2, "Beef\nOnions\nStock", top = 100),
                section("Directions", 2, "Brown the beef. Add onions. Simmer.", top = 200),
            ),
        )
        val candidates = config.detect(page)
        assertThat(candidates).hasSize(1)
        assertThat(candidates.single().title).isEqualTo("Grandma's stew")
    }

    @Test
    fun `recipe URL path relaxes the quantity threshold`() {
        val sections = listOf(section("Pancakes", 1, "2 cups flour\n1 egg\nMix well and fry.", top = 0))
        assertThat(config.detect(snapshot(url = "https://example.com/blog/pancakes-day", title = null, sections = sections))).isEmpty()
        assertThat(config.detect(snapshot(url = "https://example.com/recipes/pancakes", sections = sections))).hasSize(1)
    }

    @Test
    fun `no recipe and short readable text yields nothing, long readable text yields WholePage`() {
        val none = snapshot(url = "https://news.example.com/x", sections = listOf(section("News", 1, "Short.", top = 0)), readable = readable("Short."))
        assertThat(config.detect(none)).isEmpty()

        val long = snapshot(url = "https://news.example.com/x", readable = readable("word ".repeat(200), title = "Long article"))
        val whole = config.detect(long).single()
        assertThat(whole.id).isEqualTo(RecipeKeeperClipConfig.WHOLE_PAGE_ID)
        assertThat(whole.payload).isEqualTo(CandidatePayload.WholePage)
        assertThat(whole.title).isEqualTo("Long article")
        assertThat(whole.source).isEqualTo(CandidateSource.TEXT)
    }

    // -----------------------------------------------------------------------
    // renderLlmInput
    // -----------------------------------------------------------------------

    @Test
    fun `structured rendering is compact and starts with the page header`() {
        val page = snapshot(jsonLd = listOf(loadJsonLd("howto_sections")))
        val candidate = config.detect(page).single()

        val rendered = config.renderLlmInput(candidate, page, config.budgetChars)

        assertThat(rendered).startsWith("Page: example.com — Classic Banana Bread – Example Kitchen\n\nName: Lasagne al forno\n")
        assertThat(rendered).contains("Yield: 6 servings\n")
        assertThat(rendered).contains("Times: total 150 min\n")
        assertThat(rendered).contains("\nIngredients:\n- 500 g beef mince\n")
        assertThat(rendered).contains("\nInstructions:\n1. Brown the mince with the onion.\n2. Add the passata")
        assertThat(rendered).endsWith("Images: 3")
        assertThat(rendered).doesNotContain("Ragù") // section names are not steps
        assertThat(rendered.length).isLessThan(1_000)
    }

    @Test
    fun `text rendering includes the nested sibling sections but stops at the next recipe`() {
        val page = snapshot(
            url = "https://blog.example.com/bakes",
            sections = listOf(
                section("Lemon drizzle cake", 2, "A classic.", top = 400),
                section("Ingredients", 3, LEMON_INGREDIENTS, top = 500),
                section("Method", 3, "Cream the butter and sugar.", top = 900),
                section("Notes", 3, "Keeps for 3 days.", top = 1000),
                section("Soda bread", 2, SODA_INGREDIENTS, top = 1500),
            ),
        )
        val lemon = config.detect(page).first { it.title == "Lemon drizzle cake" }

        val rendered = config.renderLlmInput(lemon, page, config.budgetChars)

        assertThat(rendered).contains("## Ingredients\n225 g butter")
        assertThat(rendered).contains("## Method\nCream the butter and sugar.")
        assertThat(rendered).contains("## Notes\nKeeps for 3 days.")
        assertThat(rendered).doesNotContain("Soda bread")
        assertThat(rendered).doesNotContain("A classic.") // parent h2 text is not part of the h3 candidate
    }

    @Test
    fun `whole page rendering uses readable text and is budget-trimmed at a paragraph boundary`() {
        val paragraphs = (1..60).joinToString("\n\n") { i -> "Paragraph $i. " + "lorem ipsum dolor sit amet ".repeat(8).trim() }
        val page = snapshot(url = "https://news.example.com/x", readable = readable(paragraphs, title = "Long"))
        val whole = config.detect(page).single()

        val rendered = config.renderLlmInput(whole, page, budgetChars = 2_000)

        assertThat(rendered.length).isAtMost(2_000)
        assertThat(rendered).endsWith(RecipeKeeperClipConfig.TRUNCATION_MARKER)
        val body = rendered.removeSuffix(RecipeKeeperClipConfig.TRUNCATION_MARKER)
        assertThat(body).endsWith("amet") // cut between paragraphs, not mid-word
        assertThat(body.substringAfterLast("\n\n")).startsWith("Paragraph ")
    }

    @Test
    fun `rendering under budget is returned whole and budget respected for huge single paragraphs`() {
        val tiny = snapshot(url = "https://news.example.com/x", readable = readable("word ".repeat(150).trim(), title = "T"))
        val candidate = config.detect(tiny).single()
        val whole = config.renderLlmInput(candidate, tiny, config.budgetChars)
        assertThat(whole).doesNotContain(RecipeKeeperClipConfig.TRUNCATION_MARKER)
        assertThat(whole).endsWith("word")

        val trimmed = config.renderLlmInput(candidate, tiny, budgetChars = 300)
        assertThat(trimmed.length).isAtMost(300)
        assertThat(trimmed).endsWith(RecipeKeeperClipConfig.TRUNCATION_MARKER)
    }

    @Test
    fun `truncateAtParagraph prefers blank line, then newline, then space, within the last 40 percent`() {
        val marker = RecipeKeeperClipConfig.TRUNCATION_MARKER
        val text = "aaaa aaaa\n\nbbbb bbbb\ncccc cccc dddd eeee ffff gggg"
        assertThat(config.truncateAtParagraph(text, text.length)).isEqualTo(text)
        // room 15 → window "aaaa aaaa\n\nbbbb", floor 9 → the blank line at 9 wins
        assertThat(config.truncateAtParagraph(text, 15 + marker.length)).isEqualTo("aaaa aaaa$marker")
        // room 30 → blank line (9) is below floor 18, newline at 20 wins
        assertThat(config.truncateAtParagraph(text, 30 + marker.length)).isEqualTo("aaaa aaaa\n\nbbbb bbbb$marker")
        // no newline in range → last space
        val flat = "aaaa bbbb cccc dddd eeee ffff gggg hhhh"
        assertThat(config.truncateAtParagraph(flat, 22 + marker.length)).isEqualTo("aaaa bbbb cccc dddd$marker")
        // degenerate budgets never exceed the limit
        assertThat(config.truncateAtParagraph(flat, 5).length).isAtMost(5)
    }

    // -----------------------------------------------------------------------
    // fallback + pickPictures
    // -----------------------------------------------------------------------

    @Test
    fun `fallback maps STRUCTURED candidates deterministically and is null for TEXT`() {
        val structured = snapshot(jsonLd = listOf(loadJsonLd("single_recipe")))
        val recipe = config.fallback(config.detect(structured).single(), structured)!!
        assertThat(recipe.id).isEqualTo("fixed-id")
        assertThat(recipe.clippedAt).isEqualTo(1_234L)
        assertThat(recipe.sourceUrl).isEqualTo(structured.url)
        assertThat(recipe.title).isEqualTo("Classic Banana Bread")
        assertThat(recipe.ingredients).hasSize(8)

        val text = snapshot(url = "https://x.example.com/recipes/a", sections = listOf(section("Cake", 1, SODA_INGREDIENTS, top = 0)))
        assertThat(config.fallback(config.detect(text).single(), text)).isNull()
    }

    @Test
    fun `accept requires two quantity ingredients for whole-page results and nothing for detected candidates`() {
        val page = snapshot(url = "https://news.example.com/x", readable = readable("word ".repeat(200), title = "Long article"))
        val whole = config.detect(page).single()
        val invented = recipe(ingredients = listOf("Chicken", "Breadcrumbs", "Onion", "Garlic", "Egg"))
        val oneQuantity = recipe(ingredients = listOf("2 eggs", "flour", "milk"))
        val real = recipe(ingredients = listOf("2 eggs", "1 cup flour", "milk"))

        assertThat(config.accept(whole, invented)).isFalse()
        assertThat(config.accept(whole, oneQuantity)).isFalse()
        assertThat(config.accept(whole, real)).isTrue()

        val text = snapshot(url = "https://x.example.com/recipes/a", sections = listOf(section("Cake", 1, SODA_INGREDIENTS, top = 0)))
        assertThat(config.accept(config.detect(text).single(), invented)).isTrue()
        val structured = snapshot(jsonLd = listOf(loadJsonLd("single_recipe")))
        assertThat(config.accept(config.detect(structured).single(), invented)).isTrue()
    }

    @Test
    fun `pickPictures ranks JSON-LD, then og, then recipe-card images by area, de-duplicated and capped`() {
        val page = snapshot(
            jsonLd = listOf(loadJsonLd("howto_sections")),
            openGraph = mapOf("og:image" to "https://cdn.example.com/lasagne-1x1.jpg?w=1200", "twitter:image" to "https://cdn.example.com/tw.jpg"),
            images = listOf(
                image("https://cdn.example.com/ad.gif", 300, 250, top = 10),
                image("https://cdn.example.com/step-small.jpg", 400, 300, top = 2000, inCard = true),
                image("https://cdn.example.com/hero.jpg", 800, 600, top = 600, inCard = true),
                image("https://cdn.example.com/tiny.jpg", 800, 600, top = 700, inCard = true, natural = 120),
                image("https://cdn.example.com/author.jpg", 200, 200, top = 3000),
            ),
        )
        val candidate = config.detect(page).single()

        val picked = config.pickPictures(candidate, page, max = 8)

        assertThat(picked).containsExactly(
            "https://cdn.example.com/lasagne-1x1.jpg",
            "https://cdn.example.com/lasagne-4x3.jpg",
            "https://cdn.example.com/lasagne-16x9.jpg",
            "https://cdn.example.com/tw.jpg",
            "https://cdn.example.com/hero.jpg",
            "https://cdn.example.com/step-small.jpg",
            "https://cdn.example.com/ad.gif",
            "https://cdn.example.com/author.jpg",
        ).inOrder()
        assertThat(config.pickPictures(candidate, page, max = 2)).hasSize(2)
        assertThat(config.pickPictures(candidate, page, max = 0)).isEmpty()
    }

    @Test
    fun `pickPictures for TEXT candidates prefers the section's own images then proximity`() {
        val page = snapshot(
            url = "https://blog.example.com/recipes/x",
            sections = listOf(section("Cake", 1, SODA_INGREDIENTS, top = 1000, images = listOf("https://img.example.com/in-section.jpg"))),
            openGraph = mapOf("og:image" to "https://img.example.com/og.jpg"),
            images = listOf(
                image("https://img.example.com/far.jpg", 900, 900, top = 5000),
                image("https://img.example.com/near.jpg", 400, 300, top = 1100),
            ),
        )
        val candidate = config.detect(page).single()
        assertThat(config.pickPictures(candidate, page, max = 8)).containsExactly(
            "https://img.example.com/in-section.jpg",
            "https://img.example.com/og.jpg",
            "https://img.example.com/near.jpg",
            "https://img.example.com/far.jpg",
        ).inOrder()
    }

    private fun recipe(ingredients: List<String>, steps: List<String> = listOf("Mix.", "Bake.")) = ClippedRecipe(
        id = "", title = "T", description = null, servings = null, prepMinutes = null, cookMinutes = null, totalMinutes = null,
        ingredients = ingredients, steps = steps, notes = null, sourceUrl = "", sourceHost = "", pictures = emptyList(),
        truncated = false, clippedAt = 0L,
    )

    private fun website(): JsonLdNode =
        JsonLdNode(listOf("WebSite"), JSONObject().put("@type", "WebSite").put("name", "Example Kitchen"))

    companion object {
        const val LEMON_INGREDIENTS = "225 g butter, softened\n225 g caster sugar\n4 eggs\n225 g self-raising flour\nZest of 2 lemons"
        const val SODA_INGREDIENTS = "450 g plain flour\n1 tsp bicarbonate of soda\n1 tsp salt\n400 ml buttermilk\nMix, shape, bake 30 minutes."
    }
}
