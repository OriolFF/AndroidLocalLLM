package com.llmlocal.core.domain.repository

import com.llmlocal.core.domain.model.RecipeEvent
import com.llmlocal.core.model.Ingredient
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level repository for recipe generation. Implementations live in
 * :core:data and may wrap the on-device LLM, a remote service, or a fake
 * for tests.
 */
interface RecipeRepository {

    /**
     * Streams [RecipeEvent]s as the recipe is generated.
     *
     * The flow completes (with a [RecipeEvent.Complete] or
     * [RecipeEvent.Failed]) exactly once per call. Cancelling the
     * collector cancels the underlying generation.
     */
    fun generateRecipeStream(ingredients: List<Ingredient>): Flow<RecipeEvent>
}
