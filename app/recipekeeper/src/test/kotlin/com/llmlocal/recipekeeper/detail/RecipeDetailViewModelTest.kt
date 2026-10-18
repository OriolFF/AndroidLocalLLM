package com.llmlocal.recipekeeper.detail

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.library.PendingRecipeDeletes
import com.llmlocal.recipekeeper.testing.FakeRecipeRepository
import com.llmlocal.recipekeeper.testing.MainDispatcherRule
import com.llmlocal.recipekeeper.testing.TestDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRecipeRepository(
        listOf(
            FakeRecipeRepository.recipe(
                id = "a",
                title = "Tomato soup",
                clippedAt = 1L,
                ingredients = listOf("tomatoes", "basil", "oil"),
                checked = setOf(0),
            ),
        ),
    )
    private val dispatchers = TestDispatchers(mainDispatcherRule.dispatcher)
    private val pendingDeletes = PendingRecipeDeletes(repository, dispatchers)

    private fun viewModel(id: String = "a") =
        RecipeDetailViewModel(id, repository, pendingDeletes, dispatchers)

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
        assertThat(vm.state.value.recipe?.checkedIngredients).containsExactly(0, 2)
        advanceUntilIdle()
        assertThat(repository.checkedCalls).containsExactly("a" to setOf(0, 2))

        vm.onIntent(RecipeDetailIntent.ToggleIngredient(0))
        advanceUntilIdle()
        assertThat(vm.state.value.recipe?.checkedIngredients).containsExactly(2)
        assertThat(repository.current.single().checkedIngredients).containsExactly(2)
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
            assertThat(awaitItem()).isEqualTo(RecipeDetailEffect.ShareRecipe("Tomato soup", "https://example.com/a"))
            vm.onIntent(RecipeDetailIntent.OpenOriginal)
            assertThat(awaitItem()).isEqualTo(RecipeDetailEffect.NavigateToBrowser("https://example.com/a"))
            vm.onIntent(RecipeDetailIntent.ReExtract)
            assertThat(awaitItem()).isEqualTo(RecipeDetailEffect.NavigateToBrowser("https://example.com/a"))
        }
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
}
