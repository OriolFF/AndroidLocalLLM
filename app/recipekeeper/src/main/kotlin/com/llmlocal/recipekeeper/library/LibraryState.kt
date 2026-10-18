package com.llmlocal.recipekeeper.library

import androidx.compose.runtime.Immutable
import com.llmlocal.recipekeeper.domain.RecipeSummary

/**
 * Single source of truth for the library screen.
 *
 * @property recipes what the grid shows — every recipe when [query] is
 *   blank, else the repository's search result — minus any recipe whose
 *   deletion is still inside the undo window
 * @property query current search text (only meaningful while [searchActive])
 * @property searchActive whether the search field is shown
 * @property isLoading `true` until the repository emits for the first time
 * @property modelStatus drives the no-model hero / download card
 */
@Immutable
data class LibraryState(
    val recipes: List<RecipeSummary> = emptyList(),
    val query: String = "",
    val searchActive: Boolean = false,
    val isLoading: Boolean = true,
    val modelStatus: LibraryModelStatus = LibraryModelStatus.NoModel,
) {
    /** Unfiltered library with nothing in it: show the onboarding empty state. */
    val isEmptyLibrary: Boolean
        get() = !isLoading && query.isBlank() && recipes.isEmpty()

    /** A non-blank query matched nothing. */
    val isSearchEmpty: Boolean
        get() = !isLoading && query.isNotBlank() && recipes.isEmpty()
}
