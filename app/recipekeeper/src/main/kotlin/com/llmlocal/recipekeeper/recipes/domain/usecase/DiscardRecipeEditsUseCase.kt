package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit

/** Throws an editor draft away: deletes its not-yet-committed (temp) pictures. */
class DiscardRecipeEditsUseCase(private val dispatchers: DispatcherProvider) {

    suspend operator fun invoke(edit: RecipeEdit) {
        TODO("F3")
    }
}
