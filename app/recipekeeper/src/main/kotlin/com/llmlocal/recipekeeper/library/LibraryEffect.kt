package com.llmlocal.recipekeeper.library

/** One-shot side effects from [LibraryViewModel], consumed by `LibraryRoute`. */
sealed interface LibraryEffect {
    data class NavigateToRecipe(val id: String) : LibraryEffect

    /** Open the browser; [url] is `null` for the blank start page. */
    data class NavigateToBrowser(val url: String?) : LibraryEffect

    data object NavigateToModelManagement : LibraryEffect

    /** The route formats the text with `labels.shareTemplate` and fires `ACTION_SEND`. */
    data class ShareRecipe(val title: String, val url: String) : LibraryEffect

    /** Show the "Recipe deleted / Undo" snackbar for [id]. */
    data class RecipeDeleted(val id: String) : LibraryEffect

    /** Undo arrived after the window closed. */
    data object UndoFailed : LibraryEffect
}
