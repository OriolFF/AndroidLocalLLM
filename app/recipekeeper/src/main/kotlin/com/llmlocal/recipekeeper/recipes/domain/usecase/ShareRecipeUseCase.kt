package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository
import com.llmlocal.recipekeeper.recipes.domain.RecipeShareTemplates
import kotlinx.coroutines.flow.first

/**
 * Builds the text to share for a recipe. The wording comes from the app's
 * labels through [templates], injected by the DI module: clipped recipes
 * share title + link, created recipes share their full text.
 */
class ShareRecipeUseCase(
    private val repository: RecipeRepository,
    private val templates: RecipeShareTemplates,
) {
    /** Clipped-only variant (title + link); created recipes share an empty body. */
    constructor(repository: RecipeRepository, template: (title: String, url: String) -> String) : this(
        repository,
        RecipeShareTemplates(
            clipped = template,
            created = { title, _, _, _, _ -> title },
            bullet = { it },
            numbered = { number, line -> "$number. $line" },
            ingredientsHeader = "",
            stepsHeader = "",
        ),
    )

    /** @return the share text, or `null` when the recipe does not exist */
    suspend operator fun invoke(id: String): String? {
        val recipe = repository.observe(id).first() ?: return null
        return when (recipe.origin) {
            RecipeOrigin.CLIPPED -> templates.clipped(recipe.title, recipe.sourceUrl)
            RecipeOrigin.CREATED -> templates.created(
                recipe.title,
                templates.ingredientsHeader,
                recipe.ingredients.joinToString("\n") { templates.bullet(it) },
                templates.stepsHeader,
                recipe.steps.mapIndexed { index, step -> templates.numbered(index + 1, step) }.joinToString("\n"),
            )
        }
    }
}
