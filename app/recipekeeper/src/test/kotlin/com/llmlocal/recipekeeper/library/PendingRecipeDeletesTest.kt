package com.llmlocal.recipekeeper.library

import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.testing.FakeRecipeRepository
import com.llmlocal.recipekeeper.testing.TestDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingRecipeDeletesTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val repository = FakeRecipeRepository(
        listOf(FakeRecipeRepository.recipe("a", "Soup", 1L)),
    )
    private val deletes = PendingRecipeDeletes(
        repository = repository,
        dispatchers = TestDispatchers(dispatcher),
        scope = scope.backgroundScope,
        undoWindowMs = 5_000,
    )

    @Test
    fun `schedule hides the id and deletes after the window`() = scope.runTest {
        deletes.schedule("a")
        assertThat(deletes.pending.value).containsExactly("a")
        assertThat(repository.deleteCalls).isEmpty()

        advanceTimeBy(5_001)

        assertThat(repository.deleteCalls).containsExactly("a")
        assertThat(deletes.pending.value).isEmpty()
    }

    @Test
    fun `undo inside the window keeps the recipe`() = scope.runTest {
        deletes.schedule("a")
        advanceTimeBy(1_000)

        assertThat(deletes.undo("a")).isTrue()
        advanceTimeBy(10_000)

        assertThat(repository.deleteCalls).isEmpty()
        assertThat(deletes.pending.value).isEmpty()
    }

    @Test
    fun `undo after the window reports failure`() = scope.runTest {
        deletes.schedule("a")
        advanceTimeBy(5_001)

        assertThat(deletes.undo("a")).isFalse()
    }

    @Test
    fun `commit deletes immediately and cancels the timer`() = scope.runTest {
        deletes.schedule("a")
        deletes.commit("a")

        assertThat(repository.deleteCalls).containsExactly("a")
        advanceTimeBy(10_000)
        assertThat(repository.deleteCalls).containsExactly("a")
    }

    @Test
    fun `schedule is idempotent while pending`() = scope.runTest {
        deletes.schedule("a")
        advanceTimeBy(4_000)
        deletes.schedule("a") // must not restart the window
        advanceTimeBy(1_001)

        assertThat(repository.deleteCalls).containsExactly("a")
    }
}
