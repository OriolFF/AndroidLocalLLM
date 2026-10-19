package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit
import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository

/**
 * Builds the editor draft: a blank [com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin.CREATED]
 * draft for a `null` id, the mapped saved recipe otherwise.
 */
class LoadRecipeForEditUseCase(private val repository: RecipeRepository) {

    /** @return the draft, or `null` when [id] is not `null` and unknown */
    suspend operator fun invoke(id: String?): RecipeEdit? {
        TODO("F3")
    }
}
