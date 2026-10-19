package com.llmlocal.recipekeeper.detail

/** One-shot side effects from [RecipeDetailViewModel], consumed by `RecipeDetailRoute`. */
sealed interface RecipeDetailEffect {
    /** The route formats the text with `labels.shareTemplate` and fires `ACTION_SEND`. */
    data class ShareRecipe(val title: String, val url: String) : RecipeDetailEffect

    /** Open the source page in the in-app browser. */
    data class NavigateToBrowser(val url: String) : RecipeDetailEffect

    /**
     * Delete confirmed: the recipe is inside the undo window. The host pops
     * this screen and shows the undo snackbar.
     */
    data class Deleted(val id: String) : RecipeDetailEffect

    /** "Retry pictures" downloaded nothing; the route shows `labels.retryPicturesFailed`. */
    data object PicturesRetryFailed : RecipeDetailEffect
}
