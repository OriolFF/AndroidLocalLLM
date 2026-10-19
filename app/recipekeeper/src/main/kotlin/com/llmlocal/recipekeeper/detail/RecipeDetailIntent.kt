package com.llmlocal.recipekeeper.detail

/** User actions on the recipe detail screen. */
sealed interface RecipeDetailIntent {
    /** Check / uncheck one ingredient; persisted via `RecipeRepository.setChecked`. */
    data class ToggleIngredient(val index: Int) : RecipeDetailIntent
    data object ResetChecks : RecipeDetailIntent

    /** Tap a step to highlight it (tap again to clear). */
    data class SelectStep(val index: Int) : RecipeDetailIntent

    data object ToggleKeepScreenOn : RecipeDetailIntent
    data object Share : RecipeDetailIntent

    /** "Retry pictures" after an offline clip: re-download the stored picture candidates. */
    data object RetryPictures : RecipeDetailIntent
    data object OpenOriginal : RecipeDetailIntent
    data object ReExtract : RecipeDetailIntent

    data object RequestDelete : RecipeDetailIntent
    data object DismissDelete : RecipeDetailIntent
    data object ConfirmDelete : RecipeDetailIntent
}
