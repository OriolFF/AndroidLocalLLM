package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit
import kotlinx.coroutines.withContext
import java.io.File

/** Throws an editor draft away: deletes its not-yet-committed (temp) pictures. */
class DiscardRecipeEditsUseCase(private val dispatchers: DispatcherProvider) {

    suspend operator fun invoke(edit: RecipeEdit) {
        withContext(dispatchers.io) {
            edit.pictures.filter { it.isNew }.forEach { runCatching { File(it.path).delete() } }
        }
    }
}
