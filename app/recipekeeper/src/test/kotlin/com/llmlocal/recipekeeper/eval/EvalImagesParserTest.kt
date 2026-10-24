package com.llmlocal.recipekeeper.eval

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.llmlocal.feature.webclipper.domain.imagesCandidate
import com.llmlocal.feature.webclipper.domain.input.ClipImage
import com.llmlocal.feature.webclipper.domain.input.ClipInput
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperParser
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The vision counterpart of [EvalParserTest]: the raw Gemma 4 E2B answers
 * produced by `tools/eval/screenshots/run_vision.py` (budget 280, `single`
 * = first viewport, `multi` = every viewport; see
 * `docs/research/screenshot-extraction-eval.md`) must go through the
 * production [RecipeKeeperParser] + `RecipeKeeperClipConfig.accept` the way
 * an images clip does. The eval scored them with a Python port of the line
 * grammar; this test keeps the JVM parser honest against real vision output.
 *
 * Quality bar (eval §3.2: 8/9 pages parsed per mode at 280): every golden is
 * either an accepted recipe or a refusal — an explicit `NO RECIPE` or the
 * empty template (header fields, no ingredient line) that the eval also
 * counts as refused and that `ClipUseCase` answers with one retry on fewer
 * pictures — at least 70 % of them are accepted, and the clean infographic
 * card is read in full.
 * Skipped (not failed) when the goldens are absent.
 */
class EvalImagesParserTest {

    private val parser = RecipeKeeperParser()
    private val config = EvalFixtures.clipConfig

    private data class Golden(val name: String, val text: String, val imageCount: Int)

    private fun goldens(): List<Golden> {
        val dir = javaClass.classLoader?.getResource("eval/images")?.toURI()?.let(::File) ?: return emptyList()
        return dir.listFiles { f -> f.name.endsWith("__b280.txt") }.orEmpty().sortedBy { it.name }.map { f ->
            Golden(f.name.removeSuffix(".txt"), f.readText(), if ("__multi__" in f.name) 3 else 1)
        }
    }

    private fun candidate(imageCount: Int) = imagesCandidate(
        ClipInput.Images(List(imageCount) { ClipImage(File("shot-$it.jpg"), 1080, 2400, "sha-$it") }),
        title = "Clip from images",
    )

    @Test
    fun `every vision golden is an accepted recipe or a refusal`() {
        val goldens = goldens()
        assumeTrue("no vision goldens under eval/images/ (tools/eval/screenshots outputs) — skipping", goldens.isNotEmpty())

        var accepted = 0
        val unexpected = mutableListOf<String>()
        for (g in goldens) {
            val recipe = parser.parse(g.text)
            val ok = recipe != null && config.accept(candidate(g.imageCount), recipe)
            val noRecipe = g.text.trim().startsWith(RecipeKeeperParser.NO_RECIPE_TEXT, ignoreCase = true)
            val emptyTemplate = g.text.lineSequence().none { it.trimStart().startsWith("- ") }
            val refused = !ok && (noRecipe || emptyTemplate)
            when {
                ok -> accepted++
                refused -> Unit
                else -> unexpected += g.name
            }
        }
        assertWithMessage("goldens that neither parse+accept nor refuse: $unexpected")
            .that(unexpected).isEmpty()
        assertWithMessage("accepted ${accepted}/${goldens.size}")
            .that(accepted.toDouble() / goldens.size).isAtLeast(0.7)
    }

    @Test
    fun `the infographic card is read in full from one picture`() {
        val golden = goldens().firstOrNull { it.name == "infographic_pancakes__single__b280" }
        assumeTrue("infographic golden absent — skipping", golden != null)
        val recipe = parser.parse(golden!!.text)
        assertThat(recipe).isNotNull()
        assertThat(config.accept(candidate(1), recipe!!)).isTrue()
        assertThat(recipe.ingredients).hasSize(9)
        assertThat(recipe.steps).hasSize(6)
        assertThat(recipe.servings).isEqualTo("4")
    }
}
