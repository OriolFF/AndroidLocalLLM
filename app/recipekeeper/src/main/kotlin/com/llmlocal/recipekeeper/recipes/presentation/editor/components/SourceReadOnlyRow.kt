package com.llmlocal.recipekeeper.recipes.presentation.editor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.llmlocal.recipekeeper.recipes.presentation.editor.RecipeEditorLabels

/**
 * Read-only "Clipped from <host>" row for clipped recipes: the URL is the
 * duplicate-check key and backs Open original / Re-extract, so it cannot be edited.
 */
@Composable
internal fun SourceReadOnlyRow(
    host: String,
    url: String,
    labels: RecipeEditorLabels,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        leadingContent = { Icon(Icons.Outlined.Link, contentDescription = null) },
        headlineContent = { Text(labels.sourceReadOnly(host)) },
        supportingContent = {
            Column {
                Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    labels.sourceReadOnlyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
