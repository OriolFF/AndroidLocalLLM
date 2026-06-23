package com.llmlocal.feature.recipe.mvi

/**
 * One-shot side effects emitted by the recipe ViewModel.
 *
 * Use [kotlinx.coroutines.channels.Channel] / `receiveAsFlow()` to consume
 * these in Compose without replays on configuration change.
 */
sealed interface RecipeEffect {
    data class ShowSnackbar(val message: String) : RecipeEffect
    data object ScrollToOutput : RecipeEffect

    /**
     * Tells the Route to navigate to the model management destination.
     * Consumed in [RecipeRoute] and forwarded to the `NavController`.
     */
    data object NavigateToModelManagement : RecipeEffect
}