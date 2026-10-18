package com.llmlocal.core.web

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

/**
 * Maps `src/test/resources/page_snapshot.json` — the object `capture.js`
 * returned for `tools/capture-harness/fixture-wprm.html` under jsdom — to
 * a [PageSnapshot]. Regenerate the fixture with the harness when the
 * script's output schema changes.
 */
class PageSnapshotMapperTest {

    private val captured: JSONObject by lazy {
        val text = requireNotNull(javaClass.classLoader.getResourceAsStream("page_snapshot.json"))
            .bufferedReader().use { it.readText() }
        JSONObject(text)
    }

    private val snapshot: PageSnapshot by lazy { PageSnapshotMapper.fromJson(captured, capturedAt = 1234L) }

    @Test
    fun `page level fields`() {
        assertThat(snapshot.url).isEqualTo("https://flourandspoon.example/banana-bread/")
        assertThat(snapshot.title).isEqualTo("Best Banana Bread – Flour & Spoon")
        assertThat(snapshot.siteName).isEqualTo("Flour & Spoon")
        assertThat(snapshot.capturedAt).isEqualTo(1234L)
    }

    @Test
    fun `json-ld and microdata are flattened together, malformed block dropped`() {
        val types = snapshot.jsonLd.map { it.types.single() }
        assertThat(types).containsExactly("WebPage", "Recipe", "Recipe").inOrder()
        val recipes = snapshot.jsonLd.filter { "Recipe" in it.types }
        assertThat(recipes.map { it.raw.getString("name") }).containsExactly("Best Banana Bread", "Quick Pancakes").inOrder()
        // The JSON-LD one came comment-wrapped from the page.
        assertThat(recipes[0].raw.getString("totalTime")).isEqualTo("PT1H10M")
        // The microdata one was synthesised by capture.js with nested items.
        assertThat(recipes[1].raw.getJSONArray("recipeIngredient").length()).isEqualTo(2)
        assertThat(recipes[1].raw.getJSONObject("recipeInstructions").getString("text")).isEqualTo("Mix and fry.")
        assertThat(recipes[1].raw.getString("image")).isEqualTo("https://flourandspoon.example/img/pancakes.jpg")
    }

    @Test
    fun `readable content`() {
        val readable = requireNotNull(snapshot.readable)
        assertThat(readable.title).isEqualTo("Best Banana Bread")
        assertThat(readable.byline).isNull()
        assertThat(readable.excerpt).contains("grandmother's banana bread")
        assertThat(readable.textContent).contains("3 ripe bananas")
        assertThat(readable.length).isEqualTo(readable.textContent.length)
    }

    @Test
    fun `sections keep heading, level, text, images and offset`() {
        assertThat(snapshot.sections).hasSize(6)
        val (title, why, cardTitle) = snapshot.sections
        val (ingredients, instructions, related) = snapshot.sections.drop(3)
        assertThat(title.heading).isEqualTo("Best Banana Bread")
        assertThat(title.level).isEqualTo(1)
        assertThat(title.text).isEqualTo("This is the intro story about my grandmother's banana bread.")
        assertThat(title.imageSrcs).containsExactly("https://cdn.example.com/hero-lazy.jpg")
        assertThat(why.heading).isEqualTo("Why this recipe works")
        assertThat(why.topOffset).isEqualTo(200)
        assertThat(cardTitle.level).isEqualTo(2)
        assertThat(cardTitle.text).isEmpty()
        assertThat(ingredients.level).isEqualTo(3)
        assertThat(ingredients.text).isEqualTo("3 ripe bananas\n1/3 cup melted butter\n3/4 cup sugar")
        assertThat(instructions.text).startsWith("Mash bananas.\nBake 60 minutes.")
        assertThat(instructions.imageSrcs).containsExactly(
            "https://cdn.example.com/step1.jpg",
            "https://cdn.example.com/tiny.jpg",
        ).inOrder()
        assertThat(related.heading).isEqualTo("Quick Pancakes")
    }

    @Test
    fun `images pass the size filter, resolve lazy src and flag recipe cards`() {
        assertThat(snapshot.images.map { it.src }).containsExactly(
            "https://cdn.example.com/hero-lazy.jpg",
            "https://cdn.example.com/card-320.jpg",
            "https://cdn.example.com/step1.jpg",
            "https://flourandspoon.example/img/pancakes.jpg",
        ).inOrder()
        val hero = snapshot.images[0]
        assertThat(hero.alt).isEqualTo("Banana bread on a board")
        assertThat(hero.width).isEqualTo(800)
        assertThat(hero.height).isEqualTo(600)
        assertThat(hero.naturalWidth).isEqualTo(1600)
        assertThat(hero.naturalHeight).isEqualTo(1200)
        assertThat(hero.inRecipeCard).isFalse()
        val card = snapshot.images[1]
        assertThat(card.alt).isNull()
        assertThat(card.inRecipeCard).isTrue()
        assertThat(card.top).isEqualTo(100)
    }

    @Test
    fun `open graph map keeps the first value per key`() {
        assertThat(snapshot.openGraph).containsExactly(
            "og:title", "Best Banana Bread",
            "og:site_name", "Flour & Spoon",
            "og:image", "https://cdn.example.com/banana-bread-og.jpg",
            "twitter:image", "https://cdn.example.com/banana-bread-tw.jpg",
        )
    }

    @Test
    fun `empty object maps to an empty snapshot`() {
        val empty = PageSnapshotMapper.fromJson(JSONObject("{}"), capturedAt = 1L)
        assertThat(empty.url).isEmpty()
        assertThat(empty.title).isNull()
        assertThat(empty.siteName).isNull()
        assertThat(empty.jsonLd).isEmpty()
        assertThat(empty.readable).isNull()
        assertThat(empty.sections).isEmpty()
        assertThat(empty.images).isEmpty()
        assertThat(empty.openGraph).isEmpty()
    }

    @Test
    fun `json nulls and blanks become kotlin nulls`() {
        val json = JSONObject(
            """{"url":"u","title":null,"siteName":"","readable":null,
                "sections":[{"heading":null,"level":0,"text":"t","imageSrcs":null,"topOffset":0}],
                "images":[{"src":"https://x/y.jpg","alt":null}]}""",
        )
        val mapped = PageSnapshotMapper.fromJson(json, capturedAt = 1L)
        assertThat(mapped.title).isNull()
        assertThat(mapped.siteName).isNull()
        assertThat(mapped.readable).isNull()
        assertThat(mapped.sections.single().heading).isNull()
        assertThat(mapped.sections.single().imageSrcs).isEmpty()
        assertThat(mapped.images.single().alt).isNull()
        assertThat(mapped.images.single().width).isEqualTo(0)
    }
}
