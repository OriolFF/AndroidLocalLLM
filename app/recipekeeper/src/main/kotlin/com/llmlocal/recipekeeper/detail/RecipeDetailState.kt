package com.llmlocal.recipekeeper.detail

import androidx.compose.runtime.Immutable
import com.llmlocal.recipekeeper.domain.ClippedRecipe

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
 */
@Immutable
data class RecipeDetailState(
    val recipe: ClippedRecipe? = null,
    val isLoading: Boolean = true,
    val currentStep: Int? = null,
    val keepScreenOn: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val retryingPictures: Boolean = false,
) {
    /** The id no longer resolves (deleted elsewhere, or a stale link). */
    val notFound: Boolean
        get() = !isLoading && recipe == null
}
