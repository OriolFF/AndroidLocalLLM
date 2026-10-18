package com.llmlocal.recipekeeper.library

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
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
    private val modelStatus = FakeModelStatusSource(LibraryModelStatus.NoModel)
    private val dispatchers = TestDispatchers(mainDispatcherRule.dispatcher)

    private fun viewModel(
        pendingDeletes: PendingRecipeDeletes = PendingRecipeDeletes(repository, dispatchers),
    ) = LibraryViewModel(
        repository = repository,
        modelStatusSource = modelStatus,
        pendingDeletes = pendingDeletes,
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
        val vm = viewModel(PendingRecipeDeletes(repository, dispatchers, undoWindowMs = 5_000))
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
            assertThat(awaitItem()).isEqualTo(LibraryEffect.ShareRecipe("Tomato soup", "https://example.com/a"))

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

        modelStatus.status.value = LibraryModelStatus.Ready
        advanceUntilIdle()
        assertThat(vm.state.value.modelStatus).isEqualTo(LibraryModelStatus.Ready)
    }

    @Test
    fun `empty library is reported only when unfiltered`() = runTest {
        val empty = FakeRecipeRepository()
        val vm = LibraryViewModel(empty, modelStatus, PendingRecipeDeletes(empty, dispatchers), dispatchers)
        advanceUntilIdle()

        assertThat(vm.state.value.isEmptyLibrary).isTrue()
        assertThat(vm.state.value.isSearchEmpty).isFalse()
    }
}
