package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import com.llmlocal.recipekeeper.recipes.domain.RecipeShareTemplates
import com.llmlocal.recipekeeper.testing.FakeRecipeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ShareRecipeUseCaseTest {

    private val templates = RecipeShareTemplates(
        clipped = { title, url -> "$title|$url" },
        created = { title, ih, il, sh, sl -> "$title\n\n$ih:\n$il\n\n$sh:\n$sl" },
        bullet = { "- $it" },
        numbered = { n, line -> "$n. $line" },
        ingredientsHeader = "Ingredients",
        stepsHeader = "Steps",
    )

    @Test
    fun `clipped recipe shares title and link`() = runTest {
        val repository = FakeRecipeRepository(listOf(FakeRecipeRepository.recipe("a", "Bread", 1L)))
        assertThat(ShareRecipeUseCase(repository, templates)("a")).isEqualTo("Bread|https://example.com/a")
    }

    @Test
    fun `created recipe shares bulleted ingredients and numbered steps`() = runTest {
        val created = FakeRecipeRepository.recipe("c", "Soup", 2L, ingredients = listOf("water", "salt"))
            .copy(origin = RecipeOrigin.CREATED, sourceUrl = "")
        val repository = FakeRecipeRepository(listOf(created))

        assertThat(ShareRecipeUseCase(repository, templates)("c")).isEqualTo(
            "Soup\n\nIngredients:\n- water\n- salt\n\nSteps:\n1. Mix.\n2. Bake.",
        )
    }

    @Test
    fun `unknown recipe returns null`() = runTest {
        assertThat(ShareRecipeUseCase(FakeRecipeRepository(), templates)("nope")).isNull()
    }

    @Test
    fun `images recipe has no link and shares the full text`() = runTest {
        val images = FakeRecipeRepository.recipe("i", "Pie", 3L, ingredients = listOf("apples"))
            .copy(origin = RecipeOrigin.IMAGES, sourceUrl = "", sourceHost = "")
        val repository = FakeRecipeRepository(listOf(images))

        assertThat(ShareRecipeUseCase(repository, templates)("i")).isEqualTo(
            "Pie\n\nIngredients:\n- apples\n\nSteps:\n1. Mix.\n2. Bake.",
        )
    }
}
