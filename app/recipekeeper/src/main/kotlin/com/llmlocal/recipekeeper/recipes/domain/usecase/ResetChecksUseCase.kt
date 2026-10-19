package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository

/** Unticks every ingredient of a recipe. */
class ResetChecksUseCase(private val repository: RecipeRepository) {

    /** @return [recipe] with no ingredient checked */
    suspend operator fun invoke(recipe: ClippedRecipe): ClippedRecipe {
        repository.setChecked(recipe.id, emptySet())
        return recipe.copy(checkedIngredients = emptySet())
    }
}
