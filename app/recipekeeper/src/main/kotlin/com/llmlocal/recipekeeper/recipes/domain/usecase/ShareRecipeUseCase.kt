package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository
import kotlinx.coroutines.flow.first

/**
 * Builds the text to share for a recipe. The wording comes from the app's
 * labels through [template] (`title`, `url` → text), injected by the DI module.
 */
class ShareRecipeUseCase(
    private val repository: RecipeRepository,
    private val template: (title: String, url: String) -> String,
) {
    /** @return the share text, or `null` when the recipe does not exist */
    suspend operator fun invoke(id: String): String? {
        val recipe = repository.observe(id).first() ?: return null
        return template(recipe.title, recipe.sourceUrl)
    }
}
