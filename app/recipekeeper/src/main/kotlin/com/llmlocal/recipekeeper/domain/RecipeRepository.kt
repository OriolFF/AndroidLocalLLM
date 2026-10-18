package com.llmlocal.recipekeeper.domain

import kotlinx.coroutines.flow.Flow

/**
 * Persistence seam for clipped recipes. Implemented by
 * `RoomRecipeRepository` (WS5a); if the Room/KSP gate ever fails, a
 * kotlinx-serialization file store implements the same interface.
 *
 * Undo-delete is modelled as **soft delete + later hard delete**: the
 * detail screen calls [softDelete], shows the "Deleted · Undo" snackbar for
 * ~5 s, then calls [delete] (or [restore] when the user taps Undo).
 * Soft-deleted recipes disappear from [observeAll] / [search] right away
 * but [observe] still returns them so a detail screen that is mid-pop does
 * not flash to "not found". [purgeDeleted] is the safety net for a process
 * that died inside the undo window.
 */
interface RecipeRepository {

    /** Every non-deleted recipe, newest first. */
    fun observeAll(): Flow<List<RecipeSummary>>

    /** One recipe (soft-deleted included), `null` when it does not exist. */
    fun observe(id: String): Flow<ClippedRecipe?>

    /** Inserts or replaces the recipe and its picture list. */
    suspend fun upsert(recipe: ClippedRecipe)

    /** Hides the recipe from lists; reversible with [restore]. */
    suspend fun softDelete(id: String)

    /** Reverses [softDelete]. No-op when the recipe is not soft-deleted. */
    suspend fun restore(id: String)

    /** Hard delete: removes the row, its picture rows and its picture files. */
    suspend fun delete(id: String)

    /**
     * Hard-deletes every recipe soft-deleted before [olderThanMillis]
     * (epoch). Call once at app start with `now - 5_000` or similar.
     *
     * @return the ids removed
     */
    suspend fun purgeDeleted(olderThanMillis: Long): List<String>

    /** Persists the ticked-off ingredient indices for [id]. */
    suspend fun setChecked(id: String, checked: Set<Int>)

    /** Title / ingredient `LIKE` search over non-deleted recipes, newest first. */
    fun search(query: String): Flow<List<RecipeSummary>>
}
