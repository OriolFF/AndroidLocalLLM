package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.EditableLine
import com.llmlocal.recipekeeper.recipes.domain.EditorPicture
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository
import kotlinx.coroutines.flow.first

/**
 * Builds the editor draft: a blank [com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin.CREATED]
 * draft for a `null` id, the mapped saved recipe otherwise.
 */
class LoadRecipeForEditUseCase(private val repository: RecipeRepository) {

    /** @return the draft, or `null` when [id] is not `null` and unknown */
    suspend operator fun invoke(id: String?): RecipeEdit? {
        if (id == null) {
            return RecipeEdit(
                id = null,
                origin = RecipeOrigin.CREATED,
                ingredients = listOf(EditableLine(key = 0, text = "")),
                steps = listOf(EditableLine(key = 1, text = "")),
            )
        }
        val recipe = repository.observe(id).first() ?: return null
        return RecipeEdit(
            id = recipe.id,
            origin = recipe.origin,
            title = recipe.title,
            description = recipe.description.orEmpty(),
            servings = recipe.servings.orEmpty(),
            prepMinutes = recipe.prepMinutes,
            cookMinutes = recipe.cookMinutes,
            totalMinutes = recipe.totalMinutes,
            ingredients = recipe.ingredients.mapIndexed { i, text -> EditableLine(i.toLong(), text, sourceIndex = i) },
            steps = recipe.steps.mapIndexed { i, text -> EditableLine(i.toLong(), text, sourceIndex = i) },
            notes = recipe.notes.orEmpty(),
            pictures = recipe.pictures.mapIndexed { i, path -> EditorPicture(i.toLong(), path, isNew = false) },
            sourceUrl = recipe.sourceUrl,
            sourceHost = recipe.sourceHost,
        )
    }
}
