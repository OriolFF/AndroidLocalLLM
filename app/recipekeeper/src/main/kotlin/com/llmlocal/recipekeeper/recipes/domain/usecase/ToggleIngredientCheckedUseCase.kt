package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository

/** Ticks / unticks one ingredient and persists the new set. */
class ToggleIngredientCheckedUseCase(private val repository: RecipeRepository) {

    /**
     * @return [recipe] with the new checked set, or `null` (nothing persisted)
     *   when [index] is not an ingredient of the recipe
     */
    suspend operator fun invoke(recipe: ClippedRecipe, index: Int): ClippedRecipe? {
        if (index !in recipe.ingredients.indices) return null
        val checked = recipe.checkedIngredients.toMutableSet().apply {
            if (!add(index)) remove(index)
        }
        repository.setChecked(recipe.id, checked)
        return recipe.copy(checkedIngredients = checked)
    }
}
