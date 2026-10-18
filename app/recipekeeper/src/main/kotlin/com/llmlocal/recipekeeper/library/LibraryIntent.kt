package com.llmlocal.recipekeeper.library

/** User actions on the library screen. */
sealed interface LibraryIntent {
    data object OpenSearch : LibraryIntent
    data object CloseSearch : LibraryIntent
    data class SearchChanged(val value: String) : LibraryIntent
    data object ClearSearch : LibraryIntent

    data class OpenRecipe(val id: String) : LibraryIntent
    data object ClipRecipe : LibraryIntent
    data object OpenModelManagement : LibraryIntent

    /** Long-press menu → Share: the ViewModel resolves the URL and emits [LibraryEffect.ShareRecipe]. */
    data class ShareRecipe(val id: String) : LibraryIntent

    /** Long-press menu → Open original. */
    data class OpenOriginal(val id: String) : LibraryIntent

    /** Long-press menu → Delete: starts the undo window. */
    data class DeleteRecipe(val id: String) : LibraryIntent

    /** Snackbar "Undo" tapped. */
    data class UndoDelete(val id: String) : LibraryIntent

    /** Snackbar dismissed before the window closed: delete now. */
    data class ConfirmDelete(val id: String) : LibraryIntent
}
