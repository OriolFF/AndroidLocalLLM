package com.llmlocal.recipekeeper.eval

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.llmlocal.feature.webclipper.CandidateSource
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Step 1 + 2 of the eval loop: for every captured page under
 * `eval/pages/`, run `detect` → `renderLlmInput` → `RecipeKeeperPromptBuilder`
 * exactly as the app does, assert the detection expectations and the
 * prompt budget, and write the full prompt to
 * `build/eval/prompts/<fixture>.txt` for `tools/eval/run_local_model.sh`.
 *
 * Runs without the model. Re-run it whenever the prompt, the renderer or a
 * fixture changes, then regenerate the goldens with the runner script.
 */
@RunWith(Parameterized::class)
class EvalPromptRenderingTest(private val fixture: EvalFixture) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<Array<Any>> = EvalFixtures.ALL.map { arrayOf<Any>(it) }
    }

    @Test
    fun `detect picks the expected tier and candidate count`() {
        val snapshot = EvalFixtures.load(fixture)
        val candidates = EvalFixtures.candidates(snapshot)
        val summary = candidates.joinToString { "${it.id}:${it.source}:'${it.title.take(40)}'" }

        assertWithMessage("${fixture.name}: candidates [$summary]")
            .that(candidates.size).isAtLeast(fixture.minCandidates)
        assertWithMessage("${fixture.name}: candidates [$summary]")
            .that(candidates.size).isAtMost(fixture.maxCandidates)
        assertWithMessage("${fixture.name}: top candidate tier")
            .that(EvalFixtures.tierOf(candidates.firstOrNull())).isEqualTo(fixture.tier)
        assertThat(candidates.size).isAtMost(com.llmlocal.recipekeeper.clip.RecipeKeeperClipConfig.MAX_CANDIDATES)

        if (fixture.tier == ExpectedTier.STRUCTURED) {
            assertThat(candidates.all { it.source == CandidateSource.STRUCTURED }).isTrue()
            val raw = checkNotNull(EvalFixtures.structuredRaw(candidates.first()))
            assertWithMessage("${fixture.name}: structured node has ingredients")
                .that(EvalFixtures.mapper.ingredients(raw)).isNotEmpty()
        }
    }

    @Test
    fun `rendered input fits the budget and the full prompt is written`() {
        val snapshot = EvalFixtures.load(fixture)
        val top = EvalFixtures.topCandidate(snapshot)
        val promptsDir = EvalFixtures.promptsDir.apply { mkdirs() }
        if (top == null) {
            assertThat(fixture.tier).isEqualTo(ExpectedTier.NONE)
            // Nothing to send to the model; make sure a stale prompt does not linger.
            promptFile(promptsDir).delete()
            return
        }

        val rendered = EvalFixtures.renderedInput(top, snapshot)
        val budget = EvalFixtures.clipConfig.budgetChars
        assertWithMessage("${fixture.name}: rendered input length").that(rendered.length).isAtMost(budget)
        assertThat(rendered).startsWith("Page: ")
        if (fixture.tier == ExpectedTier.STRUCTURED) {
            assertThat(rendered).contains("\nIngredients:\n")
        }

        val prompt = EvalFixtures.fullPrompt(rendered)
        assertThat(prompt).startsWith(EvalFixtures.promptBuilder.systemPrompt())
        assertThat(prompt).endsWith(rendered.trim())

        promptFile(promptsDir).writeText(prompt)
        val meta = JSONObject()
            .put("fixture", fixture.name)
            .put("sourceUrl", fixture.sourceUrl)
            .put("tier", EvalFixtures.tierOf(top).name)
            .put("candidateId", top.id)
            .put("candidateTitle", top.title)
            .put("candidates", EvalFixtures.candidates(snapshot).size)
            .put("renderedChars", rendered.length)
            .put("promptChars", prompt.length)
            .put("truncated", rendered.endsWith(com.llmlocal.recipekeeper.clip.RecipeKeeperClipConfig.TRUNCATION_MARKER))
        java.io.File(promptsDir, "${fixture.name}.meta.json").writeText(meta.toString(2) + "\n")
    }

    private fun promptFile(dir: java.io.File) = java.io.File(dir, "${fixture.name}.txt")
}

/** Guards the registry: every fixture file must be listed and vice versa. */
class EvalFixtureRegistryTest {
    @Test
    fun `every fixture file on the classpath is registered`() {
        val files = EvalFixtures.fixtureFilesOnClasspath()
        val registered = EvalFixtures.ALL.map { it.name }.toSet()
        assertWithMessage("fixture files without a registry entry").that(files - registered).isEmpty()
        assertWithMessage("registry entries without a fixture file").that(registered - files).isEmpty()
        assertThat(EvalFixtures.ALL.size).isAtLeast(8)
    }

    @Test
    fun `fixture meta records source URL and fetch date`() {
        EvalFixtures.ALL.forEach { f ->
            val meta = EvalFixtures.meta(f.name)
            assertWithMessage(f.name).that(meta.optString("sourceUrl")).isEqualTo(f.sourceUrl)
            assertWithMessage(f.name).that(meta.optString("fetchedAt")).matches("\\d{4}-\\d{2}-\\d{2}")
        }
    }
}
