package com.llmlocal.recipekeeper.clip

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.data.repository.LlmTaskRepositoryImpl
import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.usecase.GenerateLlmTaskUseCase
import com.llmlocal.core.llm.engine.LlmRequest
import com.llmlocal.feature.webclipper.ClipSaveOptions
import com.llmlocal.recipekeeper.data.RecipePictureStore
import com.llmlocal.recipekeeper.domain.ClippedRecipe
import com.llmlocal.recipekeeper.parser.RecipeKeeperParserTest
import com.llmlocal.recipekeeper.testing.FakeLlmEngine
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
import java.io.File

/**
 * End-to-end on the JVM: detect → renderLlmInput → prompt → FakeLlmEngine →
 * LlmTaskRepositoryImpl → RecipeKeeperParser → RecipeKeeperClipSink.
 */
class RecipeKeeperPipelineTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = RecipeKeeperClipConfig(durationParser = testDurationParser)
    private val useCase = GenerateLlmTaskUseCase(LlmTaskRepositoryImpl())

    @Test
    fun `scripted model output becomes a saved ClippedRecipe with pictures moved into place`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val page = snapshot(jsonLd = listOf(loadJsonLd("single_recipe")))
        val candidate = config.detect(page).single()
        val rendered = config.renderLlmInput(candidate, page, config.budgetChars)
        val engine = FakeLlmEngine.streaming(RecipeKeeperParserTest.FULL_OUTPUT, chunkSize = 7)

        var result: ClippedRecipe? = null
        var streamed = ""
        useCase(engine, config.llmConfig, listOf(rendered)).test {
            while (true) {
                when (val event = awaitItem()) {
                    is LlmTaskEvent.Token -> streamed += event.delta
                    is LlmTaskEvent.Complete -> { result = event.result; break }
                    is LlmTaskEvent.Failed -> throw AssertionError("unexpected failure", event.error)
                }
            }
            awaitComplete()
        }

        // Prompt plumbing: system + user prompt with the rendered page input.
        val request: LlmRequest = engine.requests.single()
        assertThat(request.prompt).startsWith(config.llmConfig.promptBuilder.systemPrompt())
        assertThat(request.prompt).contains("Page: example.com — Classic Banana Bread")
        assertThat(request.prompt).contains("- 3 ripe bananas, mashed")
        assertThat(streamed).isEqualTo(RecipeKeeperParserTest.FULL_OUTPUT)

        val parsed = requireNotNull(result)
        assertThat(parsed.title).isEqualTo("Classic Banana Bread")
        assertThat(parsed.id).isEmpty() // detached until saved

        // Save through the sink.
        val repository = FakeRecipeRepository()
        val store = RecipePictureStore(temp.newFolder("recipes"))
        val sink = RecipeKeeperClipSink(repository, store, TestDispatcherProvider(dispatcher), idFactory = { "saved-1" }, clock = { 99L })
        val tempPictures = listOf(temp.newFile("a.jpg").apply { writeText("A") }, temp.newFile("b.jpg").apply { writeText("B") })

        val id = sink.save(parsed, page, candidate, tempPictures, truncated = false)

        assertThat(id).isEqualTo("saved-1")
        val saved = repository.recipes.single()
        assertThat(saved.id).isEqualTo("saved-1")
        assertThat(saved.title).isEqualTo("Classic Banana Bread")
        assertThat(saved.sourceUrl).isEqualTo(page.url)
        assertThat(saved.sourceHost).isEqualTo("example.com")
        assertThat(saved.clippedAt).isEqualTo(99L)
        assertThat(saved.ingredients).hasSize(5)
        assertThat(saved.steps).hasSize(4)
        assertThat(saved.pictures).hasSize(2)
        assertThat(saved.pictures.map { File(it).name }).containsExactly("001.jpg", "002.jpg").inOrder()
        saved.pictures.forEach { assertThat(File(it).exists()).isTrue() }
        tempPictures.forEach { assertThat(it.exists()).isFalse() }
        assertThat(File(saved.pictures.first()).readText()).isEqualTo("A")
    }

    @Test
    fun `unparseable output fails the stream and the structured fallback still produces the recipe`() = runTest {
        val page = snapshot(jsonLd = listOf(loadJsonLd("single_recipe")))
        val candidate = config.detect(page).single()
        val engine = FakeLlmEngine.streaming("Sorry, I cannot find a recipe here.")

        useCase(engine, config.llmConfig, listOf("x")).test {
            while (awaitItem() is LlmTaskEvent.Token) Unit
            cancelAndIgnoreRemainingEvents()
        }
        val events = mutableListOf<LlmTaskEvent<ClippedRecipe>>()
        useCase(engine, config.llmConfig, listOf("x")).test {
            do { val e = awaitItem(); events += e } while (e is LlmTaskEvent.Token)
            awaitComplete()
        }
        assertThat(events.last()).isInstanceOf(LlmTaskEvent.Failed::class.java)

        val fallback = config.fallback(candidate, page)!!
        assertThat(fallback.title).isEqualTo("Classic Banana Bread")
        assertThat(fallback.ingredients).hasSize(8)
        assertThat(fallback.id).isNotEmpty()
        assertThat(fallback.sourceUrl).isEqualTo(page.url)
    }

    @Test
    fun `sink fills a blank title from the candidate, keeps a fallback id and ORs the truncated flag`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val page = snapshot(jsonLd = listOf(loadJsonLd("single_recipe")))
        val candidate = config.detect(page).single()
        val repository = FakeRecipeRepository()
        val sink = RecipeKeeperClipSink(repository, RecipePictureStore(temp.newFolder()), TestDispatcherProvider(dispatcher), idFactory = { "minted" })

        val detached = RecipeKeeperParserTest.FULL_OUTPUT.lines().filterNot { it.startsWith("Title:") }.joinToString("\n")
        val parsed = config.llmConfig.parser.parse(detached)!!
        assertThat(parsed.title).isEmpty()
        val id1 = sink.save(parsed, page, candidate, emptyList(), truncated = true)
        assertThat(id1).isEqualTo("minted")
        assertThat(repository.recipes.single { it.id == id1 }.title).isEqualTo("Classic Banana Bread")
        assertThat(repository.recipes.single { it.id == id1 }.truncated).isTrue()
        assertThat(repository.recipes.single { it.id == id1 }.pictures).isEmpty()

        val fromFallback = config.fallback(candidate, page)!!
        val id2 = sink.save(fromFallback, page, candidate, emptyList(), truncated = false)
        assertThat(id2).isEqualTo(fromFallback.id)
        assertThat(repository.recipes.single { it.id == id2 }.truncated).isFalse()
        assertThat(repository.recipes.single { it.id == id2 }.picturesFailed).isFalse()
    }

    @Test
    fun `sink records failed pictures and the candidate urls for a later retry`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val page = snapshot(jsonLd = listOf(loadJsonLd("single_recipe")))
        val candidate = config.detect(page).single()
        val repository = FakeRecipeRepository()
        val sink = RecipeKeeperClipSink(repository, RecipePictureStore(temp.newFolder()), TestDispatcherProvider(dispatcher), idFactory = { "offline" })
        val parsed = config.llmConfig.parser.parse(RecipeKeeperParserTest.FULL_OUTPUT)!!
        val urls = listOf("https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg")

        sink.save(parsed, page, candidate, emptyList(), truncated = false, ClipSaveOptions(pictureCandidates = urls, picturesFailed = true))
        val saved = repository.recipes.single()
        assertThat(saved.picturesFailed).isTrue()
        assertThat(saved.pictureCandidates).isEqualTo(urls)
        assertThat(saved.pictures).isEmpty()

        // A picture that did make it to disk wins over the flag.
        val file = temp.newFile("ok.jpg").apply { writeText("x") }
        val id = sink.save(parsed.copy(id = "second"), page, candidate, listOf(file), truncated = false, ClipSaveOptions(pictureCandidates = urls, picturesFailed = true))
        assertThat(repository.recipes.single { it.id == id }.picturesFailed).isFalse()
    }
}
