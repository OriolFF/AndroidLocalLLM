package com.llmlocal.recipekeeper.clip.domain

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.web.JsonLdNode
import com.llmlocal.feature.webclipper.domain.CandidatePayload
import com.llmlocal.feature.webclipper.domain.CandidateSource
import com.llmlocal.feature.webclipper.domain.PicturePick
import com.llmlocal.feature.webclipper.domain.wholePageCandidate
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
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
        untitledTitle = "Untitled recipe",
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
    fun `collection-page Recipe stubs without ingredients or instructions are not candidates`() {
        // tasteofhome.com/collection/…: one Recipe node per card with name /
        // description / image / cookTime only (WS10 bug 3).
        val card = loadJsonLd("collection_card")
        val stubs = listOf("Unstuffed Peppers", "Beef Taco Skillet", "Cheeseburger Soup").map { name ->
            JsonLdNode(listOf("Recipe"), JSONObject(card.raw.toString()).put("name", name))
        }
        val collection = snapshot(
            url = "https://www.example.com/collection/recipes-with-ground-beef/",
            jsonLd = stubs + website(),
            readable = readable("word ".repeat(400), title = "40 Recipes with Ground Beef"),
        )

        assertThat(config.detect(collection)).isEmpty()

        // A real recipe node on the same page is still found, and only it.
        val mixed = snapshot(jsonLd = stubs + loadJsonLd("single_recipe"))
        val candidates = config.detect(mixed)
        assertThat(candidates.map { it.title }).containsExactly("Classic Banana Bread")
        assertThat(candidates.single().id).isEqualTo("structured-0")
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
    fun `a page with no recipe yields nothing, however long its readable text`() {
        // WS10 bug 2: a WholePage candidate from detect() made single-candidate
        // routing skip the "No recipe found" sheet on list / home pages.
        val none = snapshot(url = "https://news.example.com/x", sections = listOf(section("News", 1, "Short.", top = 0)), readable = readable("Short."))
        assertThat(config.detect(none)).isEmpty()

        val long = snapshot(url = "https://news.example.com/x", readable = readable("word ".repeat(2_000), title = "Long article"))
        assertThat(config.detect(long)).isEmpty()

        // "Extract anyway" is the feature's synthetic candidate; the config still renders it.
        val whole = wholePageCandidate(long)
        assertThat(whole.payload).isEqualTo(CandidatePayload.WholePage)
        assertThat(whole.source).isEqualTo(CandidateSource.TEXT)
        assertThat(config.renderLlmInput(whole, long, config.budgetChars)).startsWith("Page: news.example.com — Long article\n\nword word")
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
    fun `text rendering prepends the preceding prose section when the method is above the ingredients`() {
        // Mirrors joyofbaking.com: h1 holds the intro + method prose, the ingredient list is a later h2.
        val page = joyOfBakingLikePage()
        val candidate = config.detect(page).single()
        assertThat((candidate.payload as CandidatePayload.Text).section.heading).isEqualTo("Pancake Recipe:")

        val rendered = config.renderLlmInput(candidate, page, config.budgetChars)

        val method = rendered.indexOf("In a large bowl whisk together")
        val ingredients = rendered.indexOf("## Pancake Recipe:\n1 cup (130 grams) all-purpose flour")
        assertThat(method).isGreaterThan(0)
        assertThat(ingredients).isGreaterThan(method) // prose first, then the candidate
        assertThat(rendered).contains("## Pancakes Recipe & Video\n")
        assertThat(rendered).doesNotContain(RecipeKeeperClipConfig.HEAD_MARKER)
        assertThat(rendered.length).isAtMost(config.budgetChars)
    }

    @Test
    fun `preceding prose is cut from its start to keep the candidate whole within a small budget`() {
        val page = joyOfBakingLikePage()
        val candidate = config.detect(page).single()
        val candidateText = "## Pancake Recipe:\n" + PANCAKE_INGREDIENTS

        val rendered = config.renderLlmInput(candidate, page, budgetChars = 1_200)

        assertThat(rendered.length).isAtMost(1_200)
        assertThat(rendered).contains(candidateText)
        assertThat(rendered).contains("## Pancakes Recipe & Video\n" + RecipeKeeperClipConfig.HEAD_MARKER)
        assertThat(rendered).doesNotContain("My idea of the perfect weekend breakfast") // the intro went first
        assertThat(rendered).contains("Serve immediately with butter and maple syrup.") // the tail of the method stayed
        assertThat(rendered).doesNotContain(RecipeKeeperClipConfig.TRUNCATION_MARKER)
    }

    @Test
    fun `no preceding prose is pulled in when the following sections already hold the method or the budget is tiny`() {
        val withMethod = snapshot(
            url = "https://blog.example.com/bakes",
            sections = listOf(
                section("Pancakes", 1, "Long intro. ".repeat(40), top = 0),
                section("Ingredients", 2, LEMON_INGREDIENTS, top = 500),
                section("Method", 2, "Whisk and fry.", top = 900),
            ),
        )
        val rendered = config.renderLlmInput(config.detect(withMethod).single(), withMethod, config.budgetChars)
        assertThat(rendered).doesNotContain("Long intro.")

        val page = joyOfBakingLikePage()
        val tiny = config.renderLlmInput(config.detect(page).single(), page, budgetChars = 500)
        assertThat(tiny).doesNotContain("## Pancakes Recipe & Video")
        assertThat(tiny.length).isAtMost(500)
    }

    @Test
    fun `trimHeadAtParagraph keeps the heading and the tail, cutting at the first boundary in the first 40 percent`() {
        val marker = RecipeKeeperClipConfig.HEAD_MARKER
        val text = "## Head\naaaa aaaa\n\nbbbb bbbb\ncccc cccc dddd eeee"
        assertThat(config.trimHeadAtParagraph(text, text.length)).isEqualTo(text)
        // room for the last 25 chars of the body: "bbb bbbb\ncccc cccc dddd eeee" → first newline (8) is within 40%
        assertThat(config.trimHeadAtParagraph(text, "## Head\n".length + marker.length + 25)).isEqualTo("## Head\n${marker}cccc cccc dddd eeee")
        // no heading: window "cc dddd" (room 7, ceiling 2) → the space at 2 is in range → "dddd"
        assertThat(config.trimHeadAtParagraph("aaaa bbbb cccc dddd", marker.length + 7)).isEqualTo("${marker}dddd")
        // window "cccc dddd" (room 9, ceiling 3): the space at 4 is out of range, so the whole window stays
        assertThat(config.trimHeadAtParagraph("aaaa bbbb cccc dddd", marker.length + 9)).isEqualTo("${marker}cccc dddd")
        assertThat(config.trimHeadAtParagraph("aaaa bbbb cccc dddd", 3).length).isAtMost(3)
    }

    @Test
    fun `whole page rendering uses readable text and is budget-trimmed at a paragraph boundary`() {
        val paragraphs = (1..60).joinToString("\n\n") { i -> "Paragraph $i. " + "lorem ipsum dolor sit amet ".repeat(8).trim() }
        val page = snapshot(url = "https://news.example.com/x", readable = readable(paragraphs, title = "Long"))
        val whole = wholePageCandidate(page)

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
        val candidate = wholePageCandidate(tiny)
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
    fun `isComplete needs three ingredients and two steps, and the skip switch is on`() {
        assertThat(config.skipModelWhenStructuredComplete).isTrue()
        val steps = listOf("Mix the dry ingredients with the eggs.", "Bake for thirty minutes until golden.")
        assertThat(config.isComplete(recipe(ingredients = listOf("1 egg", "2 cups flour", "milk"), steps = steps))).isTrue()
        assertThat(config.isComplete(recipe(ingredients = listOf("1 egg", "2 cups flour"), steps = steps))).isFalse()
        assertThat(config.isComplete(recipe(ingredients = listOf("1 egg", "2 cups flour", "milk"), steps = listOf("Mix and bake.")))).isFalse()
        // Nigella's microdata: 12 ingredients, instructions lumped into one step → the model should still run.
        val structured = snapshot(jsonLd = listOf(loadJsonLd("single_recipe")))
        assertThat(config.isComplete(config.fallback(config.detect(structured).single(), structured)!!)).isTrue()
    }

    @Test
    fun `isComplete also needs substance - short steps and dash-joined ingredients fail`() {
        val ingredients = listOf("1 egg", "2 cups flour", "milk")
        // Mean 5 chars, total 10: paraphrase-thin.
        assertThat(config.isComplete(recipe(ingredients = ingredients, steps = listOf("Mix.", "Bake.")))).isFalse()
        // Short steps but a long total method still passes (≥ 120 chars).
        val many = List(8) { "Fold gently, then rest." }
        assertThat(config.isComplete(recipe(ingredients = ingredients, steps = many))).isTrue()
        // receptes.cat: the whole list in one line.
        val joined = listOf("1 egg", "2 cups flour - 75 g butter - 1 tsp salt - 3 apples", "milk")
        val steps = listOf("Mix the dry ingredients with the eggs.", "Bake for thirty minutes until golden.")
        assertThat(config.isComplete(recipe(ingredients = joined, steps = steps))).isFalse()
    }

    @Test
    fun `accept requires two quantity ingredients for whole-page results`() {
        val page = snapshot(url = "https://news.example.com/x", readable = readable("word ".repeat(200), title = "Long article"))
        val whole = wholePageCandidate(page)
        val invented = recipe(ingredients = listOf("Chicken", "Breadcrumbs", "Onion", "Garlic", "Egg"))
        val oneQuantity = recipe(ingredients = listOf("2 eggs", "flour", "milk"))
        val real = recipe(ingredients = listOf("2 eggs", "1 cup flour", "milk"))

        assertThat(config.accept(whole, invented)).isFalse()
        assertThat(config.accept(whole, oneQuantity)).isFalse()
        assertThat(config.accept(whole, real)).isTrue()
        // Steps alone do not rescue a whole-page result: the model invents those too.
        assertThat(config.accept(whole, recipe(ingredients = listOf("Chicken", "Onion"), steps = listOf("Brown.", "Simmer.", "Serve.")))).isFalse()
    }

    @Test
    fun `accept needs two quantity lines or two steps for detected candidates`() {
        val text = snapshot(url = "https://x.example.com/recipes/a", sections = listOf(section("Cake", 1, SODA_INGREDIENTS, top = 0)))
        val textCandidate = config.detect(text).single()
        val structured = snapshot(jsonLd = listOf(loadJsonLd("single_recipe")))
        val structuredCandidate = config.detect(structured).single()

        // The WS10 "Unstuffed Peppers" row: one quantity line, one tip sentence as the step.
        val stub = recipe(ingredients = listOf("ground beef", "2 cups leftover cooked rice"), steps = listOf("Serve with a dollop of sour cream."))
        val stepsOnly = recipe(ingredients = listOf("Chicken", "Breadcrumbs", "Onion"), steps = listOf("Coat.", "Fry."))
        val quantitiesOnly = recipe(ingredients = listOf("2 eggs", "1 cup flour", "milk"), steps = listOf("Mix and bake."))
        val nothing = recipe(ingredients = emptyList(), steps = emptyList())

        for (candidate in listOf(textCandidate, structuredCandidate)) {
            assertThat(config.accept(candidate, stub)).isFalse()
            assertThat(config.accept(candidate, nothing)).isFalse()
            assertThat(config.accept(candidate, stepsOnly)).isTrue()
            assertThat(config.accept(candidate, quantitiesOnly)).isTrue()
        }
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

        // Single recipe node: its own images and og are preferred, the page pool is offered unchecked.
        assertThat(config.rankPictures(candidate, page, max = 8).map { it.preferred })
            .containsExactly(true, true, true, true, false, false, false, false).inOrder()
    }

    @Test
    fun `rankPictures on a round-up page offers only the node's own images`() {
        val page = snapshot(
            jsonLd = listOf(loadJsonLd("howto_sections"), loadJsonLd("single_recipe")),
            openGraph = mapOf("og:image" to "https://cdn.example.com/roundup-og.jpg"),
            images = listOf(
                image("https://cdn.example.com/hero.jpg", 800, 600, top = 600, inCard = true),
                image("https://cdn.example.com/other-recipe.jpg", 800, 600, top = 2600, inCard = true),
            ),
        )
        val candidates = config.detect(page)
        assertThat(candidates).hasSize(2)

        val lasagne = config.rankPictures(candidates[0], page, max = 8)
        assertThat(lasagne).containsExactly(
            PicturePick("https://cdn.example.com/lasagne-1x1.jpg"),
            PicturePick("https://cdn.example.com/lasagne-4x3.jpg"),
            PicturePick("https://cdn.example.com/lasagne-16x9.jpg"),
        ).inOrder()
        val banana = config.rankPictures(candidates[1], page, max = 8)
        assertThat(banana.map { it.url }).containsExactly("https://cdn.example.com/banana-bread.jpg")
        assertThat(banana.single().preferred).isTrue()
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
        // TEXT candidates have no structured ownership: everything stays preferred (pre-WS11 behaviour).
        assertThat(config.rankPictures(candidate, page, max = 8).all { it.preferred }).isTrue()
    }

    /** h1 intro + method prose (no instructions heading), an empty h2, then the ingredient list h2 — like joyofbaking.com. */
    private fun joyOfBakingLikePage() = snapshot(
        url = "https://www.joyofbaking.com/breakfast/Pancakes.html",
        title = "Pancakes Recipe & Video - Joyofbaking.com",
        readable = readable(PANCAKE_PROSE + "\n" + PANCAKE_INGREDIENTS, title = "Pancakes Recipe & Video"),
        sections = listOf(
            section("Pancakes Recipe & Video", 1, PANCAKE_PROSE, top = 0),
            section(null, 2, "", top = 0),
            section("Pancake Recipe:", 2, PANCAKE_INGREDIENTS, top = 100),
        ),
    )

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
        const val PANCAKE_INGREDIENTS = "1 cup (130 grams) all-purpose flour\n1 1/2 teaspoons baking powder\n1/4 teaspoon salt\n" +
            "2 tablespoons (28 grams) granulated white sugar\n1 large egg, lightly beaten\n1 cup (240 ml) milk\n" +
            "2 tablespoons (28 grams) unsalted butter, melted\nextra melted butter for greasing the pan."
        val PANCAKE_PROSE = """
            Printer Friendly Page
            My idea of the perfect weekend breakfast is a stack of Pancakes with butter and maple syrup dripping down its sides. I love how Pancakes use the most basic of ingredients. Although pancakes are mainly served for breakfast they take center stage on Pancake Day, or Shrove Tuesday, when they are served for supper.

            Related Recipes You May Like
            Blueberry Pancakes
            Buttermilk Pancakes

            Pancake Recipe: In a large bowl whisk together the flour, baking powder, salt and sugar. In a separate bowl whisk together the egg, milk, and melted butter. Make a well in the center of the dry ingredients and then pour in the egg mixture, all at once, and stir just until combined. The batter should have some small lumps.

            Heat a frying pan or griddle over medium high heat until a few sprinkles of water dropped on the pan or griddle splatter. Using a small ladle or scoop, pour about 1/4 cup (60 ml) of pancake batter onto the hot pan. When the bottoms of the pancakes are brown and bubbles start to appear on the top surfaces of the pancakes (2-3 minutes), turn over. Cook until lightly browned (about 1-2 minutes). Repeat with remaining batter.

            Serve immediately with butter and maple syrup.
            Makes about 8 - 4 inch (10 cm) pancakes. Serves 3-4 people.
        """.trimIndent()
    }
}
