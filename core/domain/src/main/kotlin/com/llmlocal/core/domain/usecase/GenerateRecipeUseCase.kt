package com.llmlocal.core.domain.usecase

import com.llmlocal.core.domain.model.RecipeEvent
import com.llmlocal.core.domain.repository.RecipeRepository
import com.llmlocal.core.model.Ingredient
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Use case for generating a recipe from a list of [Ingredient]s.
 *
 * This is a thin pass-through to the [RecipeRepository] but its existence
 * gives the presentation layer a single, intent-named entry point that can
 * be swapped or augmented (e.g. caching, validation) without touching
 * the ViewModel.
 */
class GenerateRecipeUseCase @Inject constructor(
    private val repository: RecipeRepository,
) {
    operator fun invoke(ingredients: List<Ingredient>): Flow<RecipeEvent> =
        repository.generateRecipeStream(ingredients)
}
