package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import com.llmlocal.recipekeeper.testing.FakeRecipeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LoadRecipeForEditUseCaseTest {

    private val repository = FakeRecipeRepository(
        listOf(
            FakeRecipeRepository.recipe("a", "Soup", 1L, ingredients = listOf("water", "salt"))
                .copy(description = null, pictures = listOf("/p/001.jpg")),
        ),
    )
    private val load = LoadRecipeForEditUseCase(repository)

    @Test
    fun `maps a saved recipe with stable keys and source indices`() = runTest {
        val edit = load("a")!!
        assertThat(edit.id).isEqualTo("a")
        assertThat(edit.title).isEqualTo("Soup")
        assertThat(edit.description).isEmpty()
        assertThat(edit.servings).isEqualTo("4")
        assertThat(edit.prepMinutes).isEqualTo(10)
        assertThat(edit.ingredients.map { it.text }).containsExactly("water", "salt").inOrder()
        assertThat(edit.ingredients.map { it.sourceIndex }).containsExactly(0, 1).inOrder()
        assertThat(edit.steps.map { it.key }).containsExactly(0L, 1L).inOrder()
        assertThat(edit.pictures.single().isNew).isFalse()
        assertThat(edit.sourceHost).isEqualTo("example.com")
        assertThat(edit.origin).isEqualTo(RecipeOrigin.CLIPPED)
    }

    @Test
    fun `null id builds a blank created draft with one empty row per list`() = runTest {
        val edit = load(null)!!
        assertThat(edit.id).isNull()
        assertThat(edit.origin).isEqualTo(RecipeOrigin.CREATED)
        assertThat(edit.ingredients).hasSize(1)
        assertThat(edit.steps).hasSize(1)
        assertThat(edit.ingredients.single().key).isNotEqualTo(edit.steps.single().key)
    }

    @Test
    fun `unknown id gives null`() = runTest {
        assertThat(load("nope")).isNull()
    }
}
