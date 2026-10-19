package com.llmlocal.recipekeeper.recipes.presentation.detail

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.recipes.data.FileRecipePictureStore
import com.llmlocal.recipekeeper.recipes.data.WebClipperPictureFetcher
import com.llmlocal.recipekeeper.recipes.domain.usecase.DeleteRecipeWithUndoUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ResetChecksUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.RetryRecipePicturesUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ShareRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ToggleFavouriteUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ToggleIngredientCheckedUseCase
import com.llmlocal.recipekeeper.testing.FakePictureDownloader
import com.llmlocal.recipekeeper.testing.FakeRecipeRepository
import com.llmlocal.recipekeeper.testing.MainDispatcherRule
import com.llmlocal.recipekeeper.testing.TestDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val offlineCandidates = listOf(
        "https://img.example/hero.jpg", "https://img.example/2.jpg", "https://img.example/3.jpg", "https://img.example/4.jpg",
    )
    private val repository = FakeRecipeRepository(
        listOf(
            FakeRecipeRepository.recipe(
                id = "a",
                title = "Tomato soup",
                clippedAt = 1L,
                ingredients = listOf("tomatoes", "basil", "oil"),
                checked = setOf(0),
            ),
            FakeRecipeRepository.recipe(
                id = "offline",
                title = "Clipped on a plane",
                clippedAt = 2L,
                picturesFailed = true,
                pictureCandidates = offlineCandidates,
            ),
        ),
    )
    private val dispatchers = TestDispatchers(mainDispatcherRule.dispatcher)
    private val pendingDeletes = DeleteRecipeWithUndoUseCase(repository, dispatchers)
    private val downloader = FakePictureDownloader()
    private val pictureStore by lazy { FileRecipePictureStore(File(temp.root, "recipes")) }

    private fun viewModel(id: String = "a", downloader: FakePictureDownloader = this.downloader) =
        RecipeDetailViewModel(
            recipeId = id,
            observeRecipe = ObserveRecipeUseCase(repository),
            toggleIngredient = ToggleIngredientCheckedUseCase(repository),
            resetChecks = ResetChecksUseCase(repository),
            deleteWithUndo = pendingDeletes,
            retryPictures = RetryRecipePicturesUseCase(
                repository = repository,
                fetcher = WebClipperPictureFetcher(downloader, File(temp.root, "tmp")),
                store = pictureStore,
                dispatchers = dispatchers,
            ),
            shareRecipe = ShareRecipeUseCase(repository) { title, url -> "$title|$url" },
            toggleFavourite = ToggleFavouriteUseCase(repository),
            dispatchers = dispatchers,
        )

    @Test
    fun `loads the recipe`() = runTest {
        val vm = viewModel()
        assertThat(vm.state.value.isLoading).isTrue()
        advanceUntilIdle()

        assertThat(vm.state.value.recipe?.title).isEqualTo("Tomato soup")
        assertThat(vm.state.value.notFound).isFalse()
    }

    @Test
    fun `unknown id reports not found`() = runTest {
        val vm = viewModel("missing")
        advanceUntilIdle()

        assertThat(vm.state.value.notFound).isTrue()
    }

    @Test
    fun `toggling an ingredient updates state and persists`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(RecipeDetailIntent.ToggleIngredient(2))
        advanceUntilIdle()
        assertThat(vm.state.value.recipe?.checkedIngredients).containsExactly(0, 2)
        assertThat(repository.checkedCalls).containsExactly("a" to setOf(0, 2))

        vm.onIntent(RecipeDetailIntent.ToggleIngredient(0))
        advanceUntilIdle()
        assertThat(vm.state.value.recipe?.checkedIngredients).containsExactly(2)
        assertThat(repository.current.single { it.id == "a" }.checkedIngredients).containsExactly(2)
    }

    @Test
    fun `reset clears every check`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(RecipeDetailIntent.ResetChecks)
        advanceUntilIdle()

        assertThat(vm.state.value.recipe?.checkedIngredients).isEmpty()
        assertThat(repository.checkedCalls.last()).isEqualTo("a" to emptySet<Int>())
    }

    @Test
    fun `out of range toggle is ignored`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(RecipeDetailIntent.ToggleIngredient(99))
        advanceUntilIdle()

        assertThat(repository.checkedCalls).isEmpty()
    }

    @Test
    fun `selecting a step highlights it and tapping again clears`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(RecipeDetailIntent.SelectStep(1))
        assertThat(vm.state.value.currentStep).isEqualTo(1)
        vm.onIntent(RecipeDetailIntent.SelectStep(1))
        assertThat(vm.state.value.currentStep).isNull()
    }

    @Test
    fun `keep screen on toggles`() = runTest {
        val vm = viewModel()
        vm.onIntent(RecipeDetailIntent.ToggleKeepScreenOn)
        assertThat(vm.state.value.keepScreenOn).isTrue()
        vm.onIntent(RecipeDetailIntent.ToggleKeepScreenOn)
        assertThat(vm.state.value.keepScreenOn).isFalse()
    }

    @Test
    fun `share and open original emit effects with the source url`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(RecipeDetailIntent.Share)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(RecipeDetailEffect.ShareRecipe("Tomato soup|https://example.com/a"))
            vm.onIntent(RecipeDetailIntent.OpenOriginal)
            assertThat(awaitItem()).isEqualTo(RecipeDetailEffect.NavigateToBrowser("https://example.com/a"))
            vm.onIntent(RecipeDetailIntent.ReExtract)
            assertThat(awaitItem()).isEqualTo(RecipeDetailEffect.NavigateToBrowser("https://example.com/a"))
        }
    }

    @Test
    fun `retry pictures downloads the first three candidates into the recipe folder and clears the flag`() = runTest {
        val vm = viewModel("offline")
        advanceUntilIdle()
        assertThat(vm.state.value.recipe?.picturesFailed).isTrue()

        vm.effects.test {
            vm.onIntent(RecipeDetailIntent.RetryPictures)
            assertThat(vm.state.value.retryingPictures).isTrue()
            advanceUntilIdle()
            expectNoEvents()
        }

        assertThat(downloader.requests).isEqualTo(offlineCandidates.take(RetryRecipePicturesUseCase.RETRY_PICTURES))
        val (id, paths, failed) = repository.replacePicturesCalls.single()
        assertThat(id).isEqualTo("offline")
        assertThat(failed).isFalse()
        assertThat(paths).hasSize(3)
        assertThat(paths.map { File(it).name }).containsExactly("001.jpg", "002.jpg", "003.jpg").inOrder()
        paths.forEach { assertThat(File(it).parentFile).isEqualTo(pictureStore.dirFor("offline")) }
        assertThat(File(temp.root, "tmp").listFiles().orEmpty()).isEmpty() // temp files were moved
        val state = vm.state.value
        assertThat(state.retryingPictures).isFalse()
        assertThat(state.recipe?.picturesFailed).isFalse()
        assertThat(state.recipe?.pictures).isEqualTo(paths)
    }

    @Test
    fun `retry pictures that fails again keeps the flag and reports it`() = runTest {
        val stillOffline = FakePictureDownloader(failing = offlineCandidates.toSet())
        val vm = viewModel("offline", stillOffline)
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(RecipeDetailIntent.RetryPictures)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(RecipeDetailEffect.PicturesRetryFailed)
        }
        assertThat(repository.replacePicturesCalls.single().third).isTrue()
        assertThat(vm.state.value.recipe?.picturesFailed).isTrue()
        assertThat(vm.state.value.retryingPictures).isFalse()
    }

    @Test
    fun `retry pictures without stored candidates only reports failure`() = runTest {
        val vm = viewModel("a")
        advanceUntilIdle()
        vm.effects.test {
            vm.onIntent(RecipeDetailIntent.RetryPictures)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(RecipeDetailEffect.PicturesRetryFailed)
        }
        assertThat(downloader.requests).isEmpty()
        assertThat(repository.replacePicturesCalls).isEmpty()
    }

    @Test
    fun `delete flow asks, then schedules and emits Deleted`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(RecipeDetailIntent.RequestDelete)
            assertThat(vm.state.value.showDeleteDialog).isTrue()
            vm.onIntent(RecipeDetailIntent.DismissDelete)
            assertThat(vm.state.value.showDeleteDialog).isFalse()
            expectNoEvents()

            vm.onIntent(RecipeDetailIntent.RequestDelete)
            vm.onIntent(RecipeDetailIntent.ConfirmDelete)
            assertThat(awaitItem()).isEqualTo(RecipeDetailEffect.Deleted("a"))
            assertThat(vm.state.value.showDeleteDialog).isFalse()
            assertThat(pendingDeletes.pending.value).containsExactly("a")
            assertThat(repository.deleteCalls).isEmpty()
        }
    }

    @Test
    fun `edit emits NavigateToEditor`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(RecipeDetailIntent.Edit)
            assertThat(awaitItem()).isEqualTo(RecipeDetailEffect.NavigateToEditor("a"))
        }
    }

    @Test
    fun `toggle favourite persists and flips back`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.recipe?.isFavourite).isFalse()

        vm.onIntent(RecipeDetailIntent.ToggleFavourite)
        advanceUntilIdle()
        assertThat(repository.favouriteCalls).containsExactly("a" to true)
        assertThat(vm.state.value.recipe?.isFavourite).isTrue()

        vm.onIntent(RecipeDetailIntent.ToggleFavourite)
        advanceUntilIdle()
        assertThat(repository.favouriteCalls.last()).isEqualTo("a" to false)
        assertThat(vm.state.value.recipe?.isFavourite).isFalse()
    }

    @Test
    fun `current step is cleared when the steps shrink under it`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(RecipeDetailIntent.SelectStep(1))
        assertThat(vm.state.value.currentStep).isEqualTo(1)

        repository.upsert(repository.current.first { it.id == "a" }.copy(steps = listOf("Only step.")))
        advanceUntilIdle()

        assertThat(vm.state.value.currentStep).isNull()
    }
}
