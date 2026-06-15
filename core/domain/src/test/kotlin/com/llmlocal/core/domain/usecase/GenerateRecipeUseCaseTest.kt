package com.llmlocal.core.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.domain.model.RecipeEvent
import com.llmlocal.core.domain.repository.RecipeRepository
import com.llmlocal.core.model.Ingredient
import com.llmlocal.core.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GenerateRecipeUseCaseTest {

    @Test
    fun `passes ingredients through and emits events from the repository`() = runTest {
        val expectedRecipe = Recipe(
            title = "Toast",
            ingredients = listOf("bread"),
            steps = listOf("Toast the bread"),
        )
        val ingredients = listOf(Ingredient("bread"))
        val fakeRepository = object : RecipeRepository {
            override fun generateRecipeStream(input: List<Ingredient>): Flow<RecipeEvent> = flow {
                emit(RecipeEvent.Token("Title: Toast\n\n"))
                emit(RecipeEvent.Token("Steps:\n1. Toast the bread\n"))
                emit(RecipeEvent.Complete(expectedRecipe))
            }
        }

        val useCase = GenerateRecipeUseCase(fakeRepository)
        useCase(ingredients).test {
            // First two emissions are tokens, then Complete.
            assertThat(awaitItem()).isInstanceOf(RecipeEvent.Token::class.java)
            assertThat(awaitItem()).isInstanceOf(RecipeEvent.Token::class.java)
            val complete = awaitItem()
            assertThat(complete).isInstanceOf(RecipeEvent.Complete::class.java)
            assertThat((complete as RecipeEvent.Complete).recipe).isEqualTo(expectedRecipe)
            awaitComplete()
        }
    }
}
