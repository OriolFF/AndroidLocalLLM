package com.llmlocal.feature.recipe.mvi

/**
 * All user-originated actions the recipe feature supports.
 *
 * Add a new sealed case whenever a new user gesture needs to be handled.
 */
sealed interface RecipeIntent {
    data class UpdateInput(val text: String) : RecipeIntent
    data object AddIngredient : RecipeIntent
    data class RemoveIngredient(val name: String) : RecipeIntent
    data object ClearIngredients : RecipeIntent
    data object GenerateRecipe : RecipeIntent
    data object CancelGeneration : RecipeIntent
    data object DismissError : RecipeIntent

    /**
     * Open the model management screen (no-op if already there). Used by
     * the [NoModelAvailableBanner] and any "manage models" entry points.
     */
    data object OpenModelManagement : RecipeIntent

    /**
     * Re-check the disk for the currently selected model. Useful after
     * the user returns from the model-management screen and has finished
     * downloading or switching models.
     */
    data object CheckModel : RecipeIntent
}