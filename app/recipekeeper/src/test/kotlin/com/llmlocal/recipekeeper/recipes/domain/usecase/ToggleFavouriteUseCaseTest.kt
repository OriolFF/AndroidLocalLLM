package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.testing.FakeRecipeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToggleFavouriteUseCaseTest {

    private val repository = FakeRecipeRepository(
        listOf(FakeRecipeRepository.recipe("a", "Tomato soup", clippedAt = 1L)),
    )
    private val toggle = ToggleFavouriteUseCase(repository)

    @Test
    fun `sets and unsets the favourite flag`() = runTest {
        toggle("a", true)
        assertThat(repository.observe("a").first()?.isFavourite).isTrue()

        toggle("a", false)
        assertThat(repository.observe("a").first()?.isFavourite).isFalse()
        assertThat(repository.favouriteCalls).containsExactly("a" to true, "a" to false).inOrder()
    }

    @Test
    fun `does not touch editedAt`() = runTest {
        toggle("a", true)
        assertThat(repository.observe("a").first()?.editedAt).isNull()
    }
}
