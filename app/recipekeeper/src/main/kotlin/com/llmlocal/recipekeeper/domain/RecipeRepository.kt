package com.llmlocal.recipekeeper.domain

import kotlinx.coroutines.flow.Flow

/**
 * Persistence seam for clipped recipes. The Room implementation lands in
 * WS5a (`RoomRecipeRepository`); if the Room/KSP gate fails, a
 * kotlinx-serialization file store implements the same interface.
 */
interface RecipeRepository {

    /** Every recipe, newest first. */
    fun observeAll(): Flow<List<RecipeSummary>>

    /** One recipe, `null` when it does not (or no longer) exist. */
    fun observe(id: String): Flow<ClippedRecipe?>

    suspend fun upsert(recipe: ClippedRecipe)

    suspend fun delete(id: String)

    /** Persists the ticked-off ingredient indices for [id]. */
    suspend fun setChecked(id: String, checked: Set<Int>)

    /** Title / ingredient `LIKE` search, newest first. */
    fun search(query: String): Flow<List<RecipeSummary>>
}
