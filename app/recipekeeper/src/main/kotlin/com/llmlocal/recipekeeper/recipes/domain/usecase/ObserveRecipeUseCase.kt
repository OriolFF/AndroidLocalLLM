package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository
import kotlinx.coroutines.flow.Flow

/** One recipe, live (soft-deleted included); `null` when it does not exist. */
class ObserveRecipeUseCase(private val repository: RecipeRepository) {
    operator fun invoke(id: String): Flow<ClippedRecipe?> = repository.observe(id)
}
