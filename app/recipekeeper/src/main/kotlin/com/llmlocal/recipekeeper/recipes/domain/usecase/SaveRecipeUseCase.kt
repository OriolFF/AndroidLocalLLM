package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.recipes.domain.EditableLine
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit
import com.llmlocal.recipekeeper.recipes.domain.RecipeEditValidator
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import com.llmlocal.recipekeeper.recipes.domain.RecipePictureStore
import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository
import com.llmlocal.recipekeeper.recipes.domain.SaveResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/** Validates and persists an editor draft (create or edit); commits new pictures, drops removed ones. */
class SaveRecipeUseCase(
    private val repository: RecipeRepository,
    private val pictureStore: RecipePictureStore,
    private val dispatchers: DispatcherProvider,
    private val idFactory: () -> String,
    private val clock: () -> Long,
) {

    suspend operator fun invoke(edit: RecipeEdit): SaveResult {
        val errors = RecipeEditValidator.validate(edit)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        return try {
            val base = edit.id?.let { id ->
                repository.observe(id).first() ?: return SaveResult.Failed(IllegalStateException("Recipe $id not found"))
            }
            val id = base?.id ?: idFactory()
            val ingredientLines = edit.ingredients.kept()
            val stepLines = edit.steps.kept()
            val prep = edit.prepMinutes
            val cook = edit.cookMinutes
            val total = edit.totalMinutes ?: if (prep != null && cook != null) prep + cook else null
            val pictures = commitPictures(id, edit, base)

            val recipe = if (base != null) {
                base.copy(
                    title = edit.title.trim(),
                    description = edit.description.trim().ifEmpty { null },
                    servings = edit.servings.trim().ifEmpty { null },
                    prepMinutes = prep,
                    cookMinutes = cook,
                    totalMinutes = total,
                    ingredients = ingredientLines.map { it.text.trim() },
                    steps = stepLines.map { it.text.trim() },
                    notes = edit.notes.trim().ifEmpty { null },
                    pictures = pictures,
                    truncated = false,
                    editedAt = clock(),
                    checkedIngredients = ingredientLines.mapIndexedNotNull { index, line ->
                        index.takeIf { line.sourceIndex in base.checkedIngredients }
                    }.toSet(),
                    picturesFailed = base.picturesFailed && pictures.isEmpty(),
                )
            } else {
                ClippedRecipe(
                    id = id,
                    title = edit.title.trim(),
                    description = edit.description.trim().ifEmpty { null },
                    servings = edit.servings.trim().ifEmpty { null },
                    prepMinutes = prep,
                    cookMinutes = cook,
                    totalMinutes = total,
                    ingredients = ingredientLines.map { it.text.trim() },
                    steps = stepLines.map { it.text.trim() },
                    notes = edit.notes.trim().ifEmpty { null },
                    sourceUrl = "",
                    sourceHost = "",
                    pictures = pictures,
                    truncated = false,
                    clippedAt = clock(),
                    origin = RecipeOrigin.CREATED,
                )
            }
            repository.upsert(recipe)
            SaveResult.Saved(id)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            SaveResult.Failed(t)
        }
    }

    private fun List<EditableLine>.kept() = filter { it.text.isNotBlank() }

    /** Moves new temp files in, deletes dropped stored files, returns the final strip order. */
    private suspend fun commitPictures(id: String, edit: RecipeEdit, base: ClippedRecipe?): List<String> =
        withContext(dispatchers.io) {
            // moveIn skips missing sources, so pair results with the sources that existed.
            val newPictures = edit.pictures.filter { it.isNew && File(it.path).isFile }
            val movedPaths = pictureStore.moveIn(id, newPictures.map { File(it.path) })
            val substitutions = newPictures.zip(movedPaths).toMap()
            val kept = edit.pictures.filter { !it.isNew }.map { it.path }.toSet()
            val dropped = base?.pictures.orEmpty().filter { it !in kept }
            if (dropped.isNotEmpty()) pictureStore.delete(id, dropped)
            edit.pictures.mapNotNull { if (it.isNew) substitutions[it] else it.path }
        }
}
