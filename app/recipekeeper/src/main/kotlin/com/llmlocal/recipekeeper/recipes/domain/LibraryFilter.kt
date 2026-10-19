package com.llmlocal.recipekeeper.recipes.domain

/** Library list filter; grows with tags / origin later. Combines with search (AND). */
data class LibraryFilter(val favouritesOnly: Boolean = false)
