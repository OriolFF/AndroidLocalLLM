package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit
import com.llmlocal.recipekeeper.recipes.domain.RecipePictureStore
import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository
import com.llmlocal.recipekeeper.recipes.domain.SaveResult

/** Validates and persists an editor draft (create or edit); commits new pictures, drops removed ones. */
class SaveRecipeUseCase(
    private val repository: RecipeRepository,
    private val pictureStore: RecipePictureStore,
    private val dispatchers: DispatcherProvider,
    private val idFactory: () -> String,
    private val clock: () -> Long,
) {

    suspend operator fun invoke(edit: RecipeEdit): SaveResult {
        TODO("F3")
    }
}
