package com.llmlocal.recipekeeper.clip.domain.detection

import com.google.common.truth.Truth.assertThat
import com.llmlocal.feature.webclipper.domain.CandidateSource
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperClipConfig
import com.llmlocal.recipekeeper.testing.FakeDetectionHintsSource
import com.llmlocal.recipekeeper.testing.hint
import com.llmlocal.recipekeeper.testing.hintsOf
import com.llmlocal.recipekeeper.testing.section
import com.llmlocal.recipekeeper.testing.shapeHint
import com.llmlocal.recipekeeper.testing.snapshot
import com.llmlocal.recipekeeper.testing.testDurationParser
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** L2: the detector becomes hint-aware through `onPageLoaded`; no hints = today's behaviour. */
class LearnedDetectionTest {

    private val source = FakeDetectionHintsSource()
    private val config = RecipeKeeperClipConfig(untitledTitle = "Untitled recipe", durationParser = testDurationParser, hintsSource = source)

    private val url = "https://www.example.com/blog/some-post"

    /** A made-up unit ("zorbs") the built-ins do not know, name-first (`Flour 200 zorbs`). */
    private val unitPage = snapshot(
        url = url,
        sections = listOf(
            section("Post title", 1, "Some chatter about the day.", top = 0),
            section("What you need", 3, "Flour 200 zorbs\nSugar 150 zorbs\nButter 100 zorbs\nMilk 250 zorbs", top = 100),
            section("Doing it", 3, "Mix everything together until smooth and bake for thirty minutes in a hot oven.", top = 200),
        ),
    )

    @Test
    fun `a page the built-ins miss is detected once the learned unit is injected`() = runTest {
        assertThat(config.detect(unitPage)).isEmpty()

        source.hints = hintsOf(hint(HintKind.UNIT, "zorbs"))
        config.onPageLoaded("example.com")

        val found = config.detect(unitPage)
        assertThat(found).hasSize(1)
        assertThat(found.single().source).isEqualTo(CandidateSource.TEXT)
        assertThat(found.single().detectedByHints).isTrue()
        assertThat(source.requestedHosts).containsExactly("example.com")
    }

    @Test
    fun `candidates the built-ins find are not flagged even with hints loaded`() = runTest {
        source.hints = hintsOf(hint(HintKind.UNIT, "zorbs"))
        config.onPageLoaded("example.com")
        val page = snapshot(url = url, sections = listOf(section("Cake", 2, "2 cups flour\n3 tbsp sugar\n1 tsp salt", top = 0)))

        assertThat(config.detect(page).single().detectedByHints).isFalse()
    }

    @Test
    fun `learned ingredient and instruction headings detect a heading-led block`() = runTest {
        val page = snapshot(
            url = url,
            sections = listOf(
                section("Gathering", 3, "Flour\nSugar\nButter", top = 0),
                section("Doing it", 3, "Mix and bake.", top = 100),
            ),
        )
        assertThat(config.detect(page)).isEmpty()

        source.hints = hintsOf(hint(HintKind.INGREDIENT_HEADING, "gathering"), hint(HintKind.INSTRUCTION_HEADING, "doing it"))
        config.onPageLoaded("example.com")

        assertThat(config.detect(page).single().detectedByHints).isTrue()
    }

    @Test
    fun `NUMBER_AFTER lets a bare trailing number count as a quantity`() = runTest {
        val page = snapshot(
            url = url,
            sections = listOf(section("Cake", 2, "Harina 200\nAzúcar 150\nMantequilla 100", top = 0)),
        )
        assertThat(config.detect(page)).isEmpty()

        source.hints = hintsOf(shapeHint(QuantityShape.NUMBER_AFTER))
        config.onPageLoaded("example.com")

        assertThat(config.detect(page)).hasSize(1)
    }

    @Test
    fun `NUMBER_WORDS counts a number word before any noun`() = runTest {
        val page = snapshot(
            url = url,
            sections = listOf(section("Tortilla", 2, "Dos huevos\nUna cebolla\nTres patatas", top = 0)),
        )
        assertThat(config.detect(page)).isEmpty()

        source.hints = hintsOf(shapeHint(QuantityShape.NUMBER_WORDS))
        config.onPageLoaded("example.com")

        assertThat(config.detect(page)).hasSize(1)
    }

    @Test
    fun `NO_UNITS needs a learned ingredients heading and at least five bare lines`() = runTest {
        fun page(lines: String) = snapshot(url = url, sections = listOf(section("Gathering", 3, lines, top = 0)))
        val five = "Flour\nSugar\nButter\nEggs\nMilk"
        source.hints = hintsOf(shapeHint(QuantityShape.NO_UNITS), hint(HintKind.INGREDIENT_HEADING, "gathering"))
        config.onPageLoaded("example.com")

        assertThat(config.detect(page(five))).hasSize(1)
        assertThat(config.detect(page("Flour\nSugar\nButter\nEggs"))).isEmpty()
        // Without the learned heading the shape alone changes nothing.
        source.hints = hintsOf(shapeHint(QuantityShape.NO_UNITS))
        config.onPageLoaded("example.com")
        assertThat(config.detect(page(five))).isEmpty()
    }

    @Test
    fun `a list page stays empty even with hints`() = runTest {
        source.hints = hintsOf(hint(HintKind.UNIT, "zorbs"), shapeHint(QuantityShape.NUMBER_AFTER))
        config.onPageLoaded("example.com")
        val cards = (1..8).map { n -> section("Card $n", 3, "2 cups flour\n1 tbsp oil\nA quick and easy recipe for the whole family.", top = n * 100) }
        val sections = listOf(section("Our recipes", 1, "Favourites.", top = 0)) + cards

        assertThat(config.detect(snapshot(url = "https://www.example.com/category/recipes/", sections = sections))).isEmpty()
        assertThat(config.detect(snapshot(url = "https://www.example.com/recipes/", sections = sections))).isEmpty()
    }

    @Test
    fun `penalised hints disappear - an empty set restores built-in behaviour`() = runTest {
        source.hints = hintsOf(hint(HintKind.UNIT, "zorbs"))
        config.onPageLoaded("example.com")
        assertThat(config.detect(unitPage)).hasSize(1)
        assertThat(config.currentHints().units()).containsExactly("zorbs")

        source.hints = DetectionHints.EMPTY // the store dropped it below the floor
        config.onPageLoaded("example.com")

        assertThat(config.detect(unitPage)).isEmpty()
        assertThat(config.currentHints()).isEqualTo(DetectionHints.EMPTY)
    }

    @Test
    fun `a failing hints source falls back to built-in detection`() = runTest {
        val failing = object : DetectionHintsSource {
            override suspend fun hintsFor(host: String?): DetectionHints = error("db closed")
        }
        val c = RecipeKeeperClipConfig(untitledTitle = "Untitled", durationParser = testDurationParser, hintsSource = failing)
        c.onPageLoaded("example.com")
        val page = snapshot(url = url, sections = listOf(section("Cake", 2, "2 cups flour\n3 tbsp sugar\n1 tsp salt", top = 0)))

        assertThat(c.detect(page)).hasSize(1)
    }

    @Test
    fun `the public built-in quantity regex ignores learned units`() {
        assertThat(RecipeKeeperClipConfig.quantityLine.containsMatchIn("Flour 200 g")).isTrue()
        assertThat(RecipeKeeperClipConfig.quantityLine.containsMatchIn("Flour 200 zorbs")).isFalse()
    }
}
