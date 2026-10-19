package com.llmlocal.recipekeeper.recipes.presentation.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.recipekeeper.recipes.domain.LibraryFilter
import com.llmlocal.recipekeeper.recipes.presentation.RecipeKeeperLibraryLabels

/** Chip row pinned between the search field and the grid (UX spec §1.2). */
@Composable
fun LibraryFilterRow(
    filter: LibraryFilter,
    labels: RecipeKeeperLibraryLabels,
    onFavouritesChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = filter.favouritesOnly,
            onClick = { onFavouritesChange(!filter.favouritesOnly) },
            label = { Text(labels.favouritesFilter) },
            leadingIcon = {
                Icon(
                    imageVector = if (filter.favouritesOnly) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}
