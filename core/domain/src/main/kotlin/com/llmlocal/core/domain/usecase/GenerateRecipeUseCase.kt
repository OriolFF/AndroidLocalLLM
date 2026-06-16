package com.llmlocal.core.domain.usecase

import com.llmlocal.core.domain.model.RecipeEvent
import com.llmlocal.core.domain.repository.RecipeRepository
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.model.Ingredient
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Use case for generating a recipe from a list of [Ingredient]s using the
 * supplied [LlmEngine].
 *
 * The caller chooses the engine at call time, which is what enables the
 * UI to swap between the real on-device model and a fake / demo engine
 * without re-creating the use case.
 */
class GenerateRecipeUseCase @Inject constructor(
    private val repository: RecipeRepository,
) {
    operator fun invoke(
        engine: LlmEngine,
        ingredients: List<Ingredient>,
    ): Flow<RecipeEvent> = repository.generateRecipeStream(engine, ingredients)
}
