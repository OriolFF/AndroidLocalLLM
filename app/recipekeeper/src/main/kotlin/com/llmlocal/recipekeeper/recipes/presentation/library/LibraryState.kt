package com.llmlocal.recipekeeper.recipes.presentation.library

import androidx.compose.runtime.Immutable
import com.llmlocal.recipekeeper.recipes.domain.LibraryFilter
import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatus
import com.llmlocal.recipekeeper.recipes.domain.RecipeSummary

/**
 * Single source of truth for the library screen.
 *
 * @property recipes what the grid shows — every recipe when [query] is
 *   blank, else the repository's search result — minus any recipe whose
 *   deletion is still inside the undo window
 * @property query current search text (only meaningful while [searchActive])
 * @property searchActive whether the search field is shown
 * @property isLoading `true` until the repository emits for the first time
 * @property filter list filter (favourites only); ANDs with [query]
 * @property modelStatus drives the no-model hero / download card
 */
@Immutable
data class LibraryState(
    val recipes: List<RecipeSummary> = emptyList(),
    val query: String = "",
    val searchActive: Boolean = false,
    val isLoading: Boolean = true,
    val modelStatus: LibraryModelStatus = LibraryModelStatus.NoModel,
    val filter: LibraryFilter = LibraryFilter(),
) {
    /** Unfiltered library with nothing in it: show the onboarding empty state. */
    val isEmptyLibrary: Boolean
        get() = !isLoading && query.isBlank() && !filter.favouritesOnly && recipes.isEmpty()

    /** Favourites filter on, no search, and no favourite yet. */
    val isFavouritesEmpty: Boolean
        get() = !isLoading && filter.favouritesOnly && query.isBlank() && recipes.isEmpty()

    /** A non-blank query matched nothing. */
    val isSearchEmpty: Boolean
        get() = !isLoading && query.isNotBlank() && recipes.isEmpty()
}
