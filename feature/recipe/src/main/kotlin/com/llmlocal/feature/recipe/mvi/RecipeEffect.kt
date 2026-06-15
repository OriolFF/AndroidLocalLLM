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
}
