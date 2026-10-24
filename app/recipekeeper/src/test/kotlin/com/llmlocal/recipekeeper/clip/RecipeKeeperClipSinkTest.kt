package com.llmlocal.recipekeeper.clip

import com.google.common.truth.Truth.assertThat
import com.llmlocal.feature.webclipper.domain.ClipSaveOptions
import com.llmlocal.feature.webclipper.domain.imagesCandidate
import com.llmlocal.feature.webclipper.domain.input.ClipImage
import com.llmlocal.feature.webclipper.domain.input.ClipInput
import com.llmlocal.recipekeeper.clip.data.RecipeKeeperClipSink
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperClipConfig
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperParserTest
import com.llmlocal.recipekeeper.recipes.data.FileRecipePictureStore
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import com.llmlocal.recipekeeper.testing.FakeRecipeRepository
import com.llmlocal.recipekeeper.testing.TestDispatcherProvider
import com.llmlocal.recipekeeper.testing.loadJsonLd
import com.llmlocal.recipekeeper.testing.snapshot
import com.llmlocal.recipekeeper.testing.testDurationParser
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecipeKeeperClipSinkTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = RecipeKeeperClipConfig(untitledTitle = "Untitled recipe", durationParser = testDurationParser)

    @Test
    fun `Replace keeps isFavourite and resets origin and editedAt`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val page = snapshot(jsonLd = listOf(loadJsonLd("single_recipe")))
        val candidate = config.detect(page).single()
        val parsed = config.llmConfig.parser.parse(RecipeKeeperParserTest.FULL_OUTPUT)!!
        val existing = FakeRecipeRepository.recipe("old", "Edited title", 1L)
            .copy(isFavourite = true, editedAt = 55L, origin = RecipeOrigin.CREATED)
        val repository = FakeRecipeRepository(listOf(existing))
        val sink = RecipeKeeperClipSink(
            repository, FileRecipePictureStore(temp.newFolder("recipes")), TestDispatcherProvider(dispatcher),
            untitledTitle = "Untitled recipe", idFactory = { "new" }, clock = { 10L },
        )

        val id = sink.save(parsed, page, candidate, emptyList(), truncated = false, ClipSaveOptions(replaceId = "old"))

        val saved = repository.recipes.single { it.id == id }
        assertThat(id).isEqualTo("old")
        assertThat(saved.isFavourite).isTrue()
        assertThat(saved.origin).isEqualTo(RecipeOrigin.CLIPPED)
        assertThat(saved.editedAt).isNull()

        // A fresh clip (Keep both) is not a favourite.
        val other = sink.save(parsed, page, candidate, emptyList(), truncated = false, ClipSaveOptions())
        assertThat(repository.recipes.single { it.id == other }.isFavourite).isFalse()
    }

    @Test
    fun `images save sets IMAGES origin, empty source, moves the pictures and learns nothing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val picture = temp.newFile("shot.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val input = ClipInput.Images(listOf(ClipImage(picture, 10, 10, "sha")))
        val parsed = config.llmConfig.parser.parse(RecipeKeeperParserTest.FULL_OUTPUT)!!
            .copy(sourceUrl = "ignored", sourceHost = "ignored")
        val repository = FakeRecipeRepository()
        val sink = RecipeKeeperClipSink(
            repository, FileRecipePictureStore(temp.newFolder("recipes")), TestDispatcherProvider(dispatcher),
            untitledTitle = "Untitled recipe", idFactory = { "img" }, clock = { 10L },
        )

        val id = sink.save(
            parsed, input, imagesCandidate(input, "Pictures"), listOf(picture), truncated = true,
            ClipSaveOptions(pictureCandidates = listOf("https://x/y.jpg")),
        )

        val saved = repository.recipes.single { it.id == id }
        assertThat(saved.origin).isEqualTo(RecipeOrigin.IMAGES)
        assertThat(saved.sourceUrl).isEmpty()
        assertThat(saved.sourceHost).isEmpty()
        assertThat(saved.truncated).isTrue()
        assertThat(saved.pictureCandidates).isEmpty()
        assertThat(saved.pictures).hasSize(1)
        assertThat(picture.exists()).isFalse()
    }

    @Test
    fun `images clips are never a duplicate`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sink = RecipeKeeperClipSink(
            FakeRecipeRepository(), FileRecipePictureStore(temp.newFolder("recipes")),
            TestDispatcherProvider(dispatcher), untitledTitle = "Untitled recipe",
        )
        assertThat(sink.existingId(ClipInput.Images(emptyList()))).isNull()
    }
}
