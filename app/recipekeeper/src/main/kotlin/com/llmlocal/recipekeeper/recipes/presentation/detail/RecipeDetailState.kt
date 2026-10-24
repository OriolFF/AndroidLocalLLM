package com.llmlocal.recipekeeper.recipes.presentation.detail

import androidx.compose.runtime.Immutable
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.recipes.domain.DetailBackdrop

/**
 * Single source of truth for the recipe detail screen.
 *
 * @property recipe the recipe being shown; `null` while loading or when it
 *   no longer exists
 * @property isLoading `true` until the repository emits for the first time
 * @property currentStep index of the step the cook tapped to keep their
 *   place, or `null`
 * @property keepScreenOn whether `FLAG_KEEP_SCREEN_ON` is requested
 * @property showDeleteDialog delete confirmation visible
 * @property retryingPictures "Retry pictures" download in flight
 * @property backdrop how the first picture colours the screen background
 *   (the "Recipe background" setting)
 */
@Immutable
data class RecipeDetailState(
    val recipe: ClippedRecipe? = null,
    val isLoading: Boolean = true,
    val currentStep: Int? = null,
    val keepScreenOn: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val retryingPictures: Boolean = false,
    val backdrop: DetailBackdrop = DetailBackdrop.DEFAULT,
) {
    /** The id no longer resolves (deleted elsewhere, or a stale link). */
    val notFound: Boolean
        get() = !isLoading && recipe == null
}
