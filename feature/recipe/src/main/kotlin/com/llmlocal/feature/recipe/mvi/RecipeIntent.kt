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
    data object CheckModel : RecipeIntent
    data object RetryModelDownload : RecipeIntent
    data object CancelModelDownload : RecipeIntent

    /** Switch between the real on-device LLM and the canned Demo engine. */
    data class SetUseDemoEngine(val enabled: Boolean) : RecipeIntent
}
