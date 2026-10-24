package com.llmlocal.recipekeeper.recipes.presentation.library

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.clip.domain.detection.ResetDetectionHintsUseCase
import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatus
import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import com.llmlocal.recipekeeper.recipes.domain.RecipeSource
import com.llmlocal.recipekeeper.recipes.domain.usecase.DeleteRecipeWithUndoUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveImagesAvailabilityUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveLearnedHintCountUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveLibraryModelStatusUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveLibraryUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ShareRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ToggleFavouriteUseCase
import com.llmlocal.recipekeeper.testing.FakeDetectionHintsStore
import com.llmlocal.recipekeeper.testing.FakeLibraryModelStatusSource
import com.llmlocal.recipekeeper.testing.FakeRecipeRepository
import com.llmlocal.recipekeeper.testing.MainDispatcherRule
import com.llmlocal.recipekeeper.testing.TestDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRecipeRepository(
        listOf(
            FakeRecipeRepository.recipe("a", "Tomato soup", clippedAt = 3L, ingredients = listOf("tomatoes", "basil")),
            FakeRecipeRepository.recipe("b", "Pancakes", clippedAt = 2L, ingredients = listOf("flour", "eggs")),
            FakeRecipeRepository.recipe("c", "Basil pesto", clippedAt = 1L, ingredients = listOf("basil", "pine nuts")),
        ),
    )
    private val modelStatus = FakeLibraryModelStatusSource(LibraryModelStatus.NoModel)
    private val hintsStore = FakeDetectionHintsStore()
    private val dispatchers = TestDispatchers(mainDispatcherRule.dispatcher)

    private var imagesEnabled = true

    private fun viewModel(
        repository: FakeRecipeRepository = this.repository,
        deleteWithUndo: DeleteRecipeWithUndoUseCase = DeleteRecipeWithUndoUseCase(repository, dispatchers),
    ) = LibraryViewModel(
        observeLibrary = ObserveLibraryUseCase(repository, deleteWithUndo),
        observeModelStatus = ObserveLibraryModelStatusUseCase(modelStatus),
        observeRecipe = ObserveRecipeUseCase(repository),
        deleteWithUndo = deleteWithUndo,
        shareRecipe = ShareRecipeUseCase(repository) { title, url -> "$title|$url" },
        toggleFavourite = ToggleFavouriteUseCase(repository),
        observeLearnedHints = ObserveLearnedHintCountUseCase(hintsStore),
        resetDetection = ResetDetectionHintsUseCase(hintsStore),
        observeImagesAvailability = ObserveImagesAvailabilityUseCase(modelStatus, enabled = imagesEnabled),
        dispatchers = dispatchers,
    )

    @Test
    fun `emits every recipe newest first once loaded`() = runTest {
        val vm = viewModel()
        assertThat(vm.state.value.isLoading).isTrue()

        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.recipes.map { it.id }).containsExactly("a", "b", "c").inOrder()
        assertThat(state.isEmptyLibrary).isFalse()
    }

    @Test
    fun `search filters by title and ingredient and clears back to all`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(LibraryIntent.OpenSearch)
        vm.onIntent(LibraryIntent.SearchChanged("basil"))
        advanceUntilIdle()
        assertThat(vm.state.value.recipes.map { it.id }).containsExactly("a", "c").inOrder()
        assertThat(vm.state.value.searchActive).isTrue()

        vm.onIntent(LibraryIntent.SearchChanged("ramen"))
        advanceUntilIdle()
        assertThat(vm.state.value.recipes).isEmpty()
        assertThat(vm.state.value.isSearchEmpty).isTrue()
        assertThat(vm.state.value.isEmptyLibrary).isFalse()

        vm.onIntent(LibraryIntent.CloseSearch)
        advanceUntilIdle()
        assertThat(vm.state.value.query).isEmpty()
        assertThat(vm.state.value.searchActive).isFalse()
        assertThat(vm.state.value.recipes).hasSize(3)
    }

    @Test
    fun `delete hides the recipe, emits the undo effect, and undo restores it`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(LibraryIntent.DeleteRecipe("b"))
            // runCurrent, not advanceUntilIdle: the latter would also run the 5 s undo timer.
            runCurrent()

            assertThat(awaitItem()).isEqualTo(LibraryEffect.RecipeDeleted("b"))
            assertThat(vm.state.value.recipes.map { it.id }).containsExactly("a", "c").inOrder()
            assertThat(repository.deleteCalls).isEmpty()

            vm.onIntent(LibraryIntent.UndoDelete("b"))
            runCurrent()

            assertThat(vm.state.value.recipes.map { it.id }).containsExactly("a", "b", "c").inOrder()
            advanceTimeBy(10_000)
            assertThat(repository.deleteCalls).isEmpty()
            expectNoEvents()
        }
    }

    @Test
    fun `delete without undo hard-deletes after the window`() = runTest {
        val vm = viewModel(deleteWithUndo = DeleteRecipeWithUndoUseCase(repository, dispatchers, undoWindowMs = 5_000))
        advanceUntilIdle()

        vm.onIntent(LibraryIntent.DeleteRecipe("b"))
        advanceTimeBy(5_001)
        advanceUntilIdle()

        assertThat(repository.deleteCalls).containsExactly("b")
        assertThat(vm.state.value.recipes.map { it.id }).containsExactly("a", "c").inOrder()
    }

    @Test
    fun `confirm delete deletes immediately`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(LibraryIntent.DeleteRecipe("a"))
        vm.onIntent(LibraryIntent.ConfirmDelete("a"))
        advanceUntilIdle()

        assertThat(repository.deleteCalls).containsExactly("a")
    }

    @Test
    fun `late undo emits UndoFailed`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(LibraryIntent.UndoDelete("never-scheduled"))
            assertThat(awaitItem()).isEqualTo(LibraryEffect.UndoFailed)
        }
    }

    @Test
    fun `share and open original resolve the source url`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(LibraryIntent.ShareRecipe("a"))
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(LibraryEffect.ShareRecipe("Tomato soup|https://example.com/a"))

            vm.onIntent(LibraryIntent.OpenOriginal("c"))
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(LibraryEffect.NavigateToBrowser("https://example.com/c"))

            vm.onIntent(LibraryIntent.ClipRecipe)
            assertThat(awaitItem()).isEqualTo(LibraryEffect.NavigateToBrowser(null))

            vm.onIntent(LibraryIntent.OpenRecipe("b"))
            assertThat(awaitItem()).isEqualTo(LibraryEffect.NavigateToRecipe("b"))
        }
    }

    @Test
    fun `model status flows into state`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.modelStatus).isEqualTo(LibraryModelStatus.NoModel)

        modelStatus.status.value = LibraryModelStatus.Ready()
        advanceUntilIdle()
        assertThat(vm.state.value.modelStatus).isEqualTo(LibraryModelStatus.Ready())
    }

    @Test
    fun `empty library is reported only when unfiltered`() = runTest {
        val empty = FakeRecipeRepository()
        val vm = viewModel(repository = empty)
        advanceUntilIdle()

        assertThat(vm.state.value.isEmptyLibrary).isTrue()
        assertThat(vm.state.value.isSearchEmpty).isFalse()
    }

    private fun favouritesRepository() = FakeRecipeRepository(
        listOf(
            FakeRecipeRepository.recipe("a", "Tomato soup", clippedAt = 3L, ingredients = listOf("tomatoes", "basil"))
                .copy(isFavourite = true),
            FakeRecipeRepository.recipe("b", "Pancakes", clippedAt = 2L),
            FakeRecipeRepository.recipe("c", "Basil pesto", clippedAt = 1L, ingredients = listOf("basil"))
                .copy(isFavourite = true, origin = RecipeOrigin.CREATED, sourceUrl = "", sourceHost = ""),
        ),
    )

    @Test
    fun `favourites filter narrows the list and turning it off restores it`() = runTest {
        val vm = viewModel(repository = favouritesRepository())
        advanceUntilIdle()
        assertThat(vm.state.value.recipes).hasSize(3)

        vm.onIntent(LibraryIntent.SetFavouritesFilter(true))
        advanceUntilIdle()
        assertThat(vm.state.value.filter.favouritesOnly).isTrue()
        assertThat(vm.state.value.recipes.map { it.id }).containsExactly("a", "c").inOrder()

        vm.onIntent(LibraryIntent.SetFavouritesFilter(false))
        advanceUntilIdle()
        assertThat(vm.state.value.recipes.map { it.id }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `favourites filter ANDs with search`() = runTest {
        val vm = viewModel(repository = favouritesRepository())
        advanceUntilIdle()

        vm.onIntent(LibraryIntent.SetFavouritesFilter(true))
        vm.onIntent(LibraryIntent.SearchChanged("pesto"))
        advanceUntilIdle()
        assertThat(vm.state.value.recipes.map { it.id }).containsExactly("c")

        vm.onIntent(LibraryIntent.SearchChanged("pancakes"))
        advanceUntilIdle()
        assertThat(vm.state.value.recipes).isEmpty()
        assertThat(vm.state.value.isSearchEmpty).isTrue()
        assertThat(vm.state.value.isFavouritesEmpty).isFalse()
    }

    @Test
    fun `no favourites reports isFavouritesEmpty and not the onboarding state`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(LibraryIntent.SetFavouritesFilter(true))
        advanceUntilIdle()

        assertThat(vm.state.value.recipes).isEmpty()
        assertThat(vm.state.value.isFavouritesEmpty).isTrue()
        assertThat(vm.state.value.isEmptyLibrary).isFalse()
    }

    @Test
    fun `toggle favourite goes through the use case and the list reflects it`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.recipes.first { it.id == "b" }.isFavourite).isFalse()

        vm.onIntent(LibraryIntent.ToggleFavourite("b", true))
        advanceUntilIdle()

        assertThat(repository.favouriteCalls).containsExactly("b" to true)
        assertThat(vm.state.value.recipes.first { it.id == "b" }.isFavourite).isTrue()

        vm.onIntent(LibraryIntent.ToggleFavourite("b", false))
        advanceUntilIdle()
        assertThat(vm.state.value.recipes.first { it.id == "b" }.isFavourite).isFalse()
    }

    @Test
    fun `create and edit emit NavigateToEditor`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(LibraryIntent.CreateRecipe)
            assertThat(awaitItem()).isEqualTo(LibraryEffect.NavigateToEditor(null))

            vm.onIntent(LibraryIntent.EditRecipe("a"))
            assertThat(awaitItem()).isEqualTo(LibraryEffect.NavigateToEditor("a"))
        }
    }

    @Test
    fun `created rows keep their origin in state`() = runTest {
        val vm = viewModel(repository = favouritesRepository())
        advanceUntilIdle()

        val byId = vm.state.value.recipes.associateBy { it.id }
        assertThat(byId.getValue("c").origin).isEqualTo(RecipeOrigin.CREATED)
        assertThat(byId.getValue("a").origin).isEqualTo(RecipeOrigin.CLIPPED)
    }

    @Test
    fun `learned hint count follows the store`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.learnedHintCount).isEqualTo(0)

        hintsStore.setCount(12)
        advanceUntilIdle()

        assertThat(vm.state.value.learnedHintCount).isEqualTo(12)
    }

    @Test
    fun `reset detection asks first, dismiss keeps everything`() = runTest {
        hintsStore.setCount(5)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(LibraryIntent.RequestResetDetection)
        assertThat(vm.state.value.resetDetectionDialogOpen).isTrue()
        vm.onIntent(LibraryIntent.DismissResetDetection)
        advanceUntilIdle()

        assertThat(vm.state.value.resetDetectionDialogOpen).isFalse()
        assertThat(hintsStore.clearCalls).isEqualTo(0)
        assertThat(vm.state.value.learnedHintCount).isEqualTo(5)
    }

    @Test
    fun `confirming reset clears the store, closes the dialog and emits DetectionReset`() = runTest {
        hintsStore.setCount(5)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(LibraryIntent.RequestResetDetection)

        vm.effects.test {
            vm.onIntent(LibraryIntent.ConfirmResetDetection)
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(LibraryEffect.DetectionReset)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(hintsStore.clearCalls).isEqualTo(1)
        assertThat(vm.state.value.resetDetectionDialogOpen).isFalse()
        assertThat(vm.state.value.learnedHintCount).isEqualTo(0)
    }

    @Test
    fun `open and dismiss toggle the source chooser`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(LibraryIntent.OpenSourceChooser)
        assertThat(vm.state.value.sourceChooserOpen).isTrue()
        vm.onIntent(LibraryIntent.DismissSourceChooser)
        assertThat(vm.state.value.sourceChooserOpen).isFalse()
    }

    @Test
    fun `web and editor rows close the sheet and navigate, voice does nothing`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(LibraryIntent.OpenSourceChooser)
            vm.onIntent(LibraryIntent.ChooseSource(RecipeSource.WEB))
            runCurrent()
            assertThat(awaitItem()).isEqualTo(LibraryEffect.NavigateToBrowser(null))
            assertThat(vm.state.value.sourceChooserOpen).isFalse()

            vm.onIntent(LibraryIntent.ChooseSource(RecipeSource.EDITOR))
            runCurrent()
            assertThat(awaitItem()).isEqualTo(LibraryEffect.NavigateToEditor(null))

            vm.onIntent(LibraryIntent.OpenSourceChooser)
            vm.onIntent(LibraryIntent.ChooseSource(RecipeSource.VOICE))
            runCurrent()
            expectNoEvents()
            assertThat(vm.state.value.sourceChooserOpen).isFalse()
        }
    }

    @Test
    fun `images row routes by availability`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            // No model yet → model management.
            assertThat(vm.state.value.imagesAvailability).isEqualTo(ImagesAvailability.NoModel)
            vm.onIntent(LibraryIntent.ChooseSource(RecipeSource.IMAGES))
            runCurrent()
            assertThat(awaitItem()).isEqualTo(LibraryEffect.NavigateToModelManagement)

            // Text-only model → the dialog, no navigation.
            modelStatus.status.value = LibraryModelStatus.Ready(supportsImages = false, modelName = "Phi")
            advanceUntilIdle()
            vm.onIntent(LibraryIntent.ChooseSource(RecipeSource.IMAGES))
            runCurrent()
            assertThat(vm.state.value.imagesAvailability).isEqualTo(ImagesAvailability.ModelLacksVision)
            assertThat(vm.state.value.imagesUnavailableDialog).isTrue()
            vm.onIntent(LibraryIntent.DismissImagesUnavailable)
            assertThat(vm.state.value.imagesUnavailableDialog).isFalse()

            // Vision model → the images clip.
            modelStatus.status.value = LibraryModelStatus.Ready(supportsImages = true)
            advanceUntilIdle()
            vm.onIntent(LibraryIntent.ChooseSource(RecipeSource.IMAGES))
            runCurrent()
            assertThat(awaitItem()).isEqualTo(LibraryEffect.NavigateToImagesClip)
            expectNoEvents()
        }
    }

    @Test
    fun `images availability is Disabled when the build flag is off, whatever the model`() = runTest {
        imagesEnabled = false
        modelStatus.status.value = LibraryModelStatus.Ready(supportsImages = true)
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.imagesAvailability).isEqualTo(ImagesAvailability.Disabled)
        vm.effects.test {
            vm.onIntent(LibraryIntent.ChooseSource(RecipeSource.IMAGES))
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `a downloading model counts as no model for images`() = runTest {
        val vm = viewModel()
        modelStatus.status.value = LibraryModelStatus.Ready(supportsImages = true)
        advanceUntilIdle()
        assertThat(vm.state.value.imagesAvailability).isEqualTo(ImagesAvailability.Available)
        modelStatus.status.value = LibraryModelStatus.NoModel
        advanceUntilIdle()
        assertThat(vm.state.value.imagesAvailability).isEqualTo(ImagesAvailability.NoModel)
    }
}
