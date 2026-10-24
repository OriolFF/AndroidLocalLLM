package com.llmlocal.recipekeeper.recipes.presentation.library

import com.llmlocal.recipekeeper.recipes.domain.RecipeSource

/** User actions on the library screen. */
sealed interface LibraryIntent {
    data object OpenSearch : LibraryIntent
    data object CloseSearch : LibraryIntent
    data class SearchChanged(val value: String) : LibraryIntent
    data object ClearSearch : LibraryIntent

    data class OpenRecipe(val id: String) : LibraryIntent
    data object ClipRecipe : LibraryIntent
    data object OpenModelManagement : LibraryIntent

    /** Top-bar gear: the settings screen (start page, models). */
    data object OpenSettings : LibraryIntent

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

    /** Favourites chip toggled. */
    data class SetFavouritesFilter(val on: Boolean) : LibraryIntent

    /** Heart tapped on a tile (or tile menu); [favourite] is the new state. */
    data class ToggleFavourite(val id: String, val favourite: Boolean) : LibraryIntent

    /** Tile menu → Edit recipe. */
    data class EditRecipe(val id: String) : LibraryIntent

    /** Overflow → "Reset learned detection": ask for confirmation first. */
    data object RequestResetDetection : LibraryIntent

    /** Reset dialog dismissed / cancelled. */
    data object DismissResetDetection : LibraryIntent

    /** Reset dialog confirmed: forget everything learned. */
    data object ConfirmResetDetection : LibraryIntent

    /** Top-bar "New recipe" / empty-state "Write a recipe". */
    data object CreateRecipe : LibraryIntent

    /** Top-bar "+" / empty-state "More ways to add…": show the source chooser. */
    data object OpenSourceChooser : LibraryIntent

    /** Source chooser dismissed without a choice. */
    data object DismissSourceChooser : LibraryIntent

    /** A row of the source chooser was tapped. */
    data class ChooseSource(val source: RecipeSource) : LibraryIntent

    /** "This model can't read images" dialog dismissed. */
    data object DismissImagesUnavailable : LibraryIntent
}
