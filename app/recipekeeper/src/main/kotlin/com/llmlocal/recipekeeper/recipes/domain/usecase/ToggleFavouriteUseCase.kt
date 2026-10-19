package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository

/** Hearts or un-hearts a recipe. */
class ToggleFavouriteUseCase(private val repository: RecipeRepository) {

    suspend operator fun invoke(id: String, favourite: Boolean) {
        TODO("F1")
    }
}
