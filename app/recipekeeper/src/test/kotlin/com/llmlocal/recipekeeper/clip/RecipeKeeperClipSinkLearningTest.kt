package com.llmlocal.recipekeeper.clip

import com.google.common.truth.Truth.assertThat
import com.llmlocal.feature.webclipper.domain.CandidatePayload
import com.llmlocal.feature.webclipper.domain.CandidateSource
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.feature.webclipper.domain.ClipSaveOptions
import com.llmlocal.recipekeeper.clip.data.RecipeKeeperClipSink
import com.llmlocal.recipekeeper.clip.data.detection.FakeDetectionHintDao
import com.llmlocal.recipekeeper.clip.data.detection.RoomDetectionHintsStore
import com.llmlocal.recipekeeper.clip.domain.detection.DeriveDetectionHintsUseCase
import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHint
import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHintsStore
import com.llmlocal.recipekeeper.clip.domain.detection.LearnDetectionHintsUseCase
import com.llmlocal.recipekeeper.recipes.data.FileRecipePictureStore
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.testing.FakeRecipeRepository
import com.llmlocal.recipekeeper.testing.TestDispatchers
import com.llmlocal.recipekeeper.testing.section
import com.llmlocal.recipekeeper.testing.snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecipeKeeperClipSinkLearningTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val candidate = ClipCandidate(
        id = "c", title = "Pan", thumbnailUrl = null, snippet = null, source = CandidateSource.TEXT,
        ingredientCount = null, totalMinutes = null, payload = CandidatePayload.WholePage,
    )
    private val recipe = ClippedRecipe(
        id = "", title = "Pan", description = null, servings = null, prepMinutes = null, cookMinutes = null, totalMinutes = null,
        ingredients = listOf("harina 250 grs", "azúcar 100 grs", "leche 1 vasito", "sal 1 pizca"),
        steps = listOf("Mezclar.", "Hornear."), notes = null, sourceUrl = "", sourceHost = "", pictures = emptyList(),
        truncated = false, clippedAt = 0L,
    )
    private val page = snapshot(
        url = "https://www.cocina.example/pan",
        sections = listOf(
            section("Ingredientes del pan", 4, "harina 250 grs\nazúcar 100 grs\nleche 1 vasito\nsal 1 pizca", top = 0),
            section("Preparación del pan", 4, "Mezclar.\nHornear.", top = 50),
        ),
    )

    private fun TestScope.sink(store: DetectionHintsStore, learn: Boolean = true): RecipeKeeperClipSink {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return RecipeKeeperClipSink(
            FakeRecipeRepository(), FileRecipePictureStore(temp.newFolder()), TestDispatchers(dispatcher),
            untitledTitle = "Untitled recipe", idFactory = { "id" }, clock = { 5L },
            learning = LearnDetectionHintsUseCase(DeriveDetectionHintsUseCase(setOf("g"), setOf("ingredientes"), clock = { 5L }), store),
            learningScope = if (learn) this else null,
        )
    }

    @Test
    fun `a kept clip from a missed page teaches the store`() = runTest {
        val dao = FakeDetectionHintDao()
        val sink = sink(RoomDetectionHintsStore(dao))

        sink.save(recipe, page, candidate, emptyList(), truncated = false, ClipSaveOptions())
        advanceUntilIdle()

        assertThat(dao.all.map { it.token }).containsAtLeast("grs", "4", "NUMBER_AFTER")
    }

    @Test
    fun `a failing store never fails the save`() = runTest {
        val failing = object : DetectionHintsStore {
            override suspend fun reinforce(hints: List<DetectionHint>) = error("disk full")
            override suspend fun penalise(host: String?, kinds: Set<com.llmlocal.recipekeeper.clip.domain.detection.HintKind>, tokens: Set<String>) = Unit
            override suspend fun clear() = Unit
            override fun count(): Flow<Int> = flowOf(0)
        }
        val id = sink(failing).save(recipe, page, candidate, emptyList(), truncated = false, ClipSaveOptions(usedHints = true))
        advanceUntilIdle()
        assertThat(id).isEqualTo("id")
    }

    @Test
    fun `without a learning scope nothing is learned`() = runTest {
        val dao = FakeDetectionHintDao()
        sink(RoomDetectionHintsStore(dao), learn = false).save(recipe, page, candidate, emptyList(), truncated = false, ClipSaveOptions())
        advanceUntilIdle()
        assertThat(dao.all).isEmpty()
    }
}
