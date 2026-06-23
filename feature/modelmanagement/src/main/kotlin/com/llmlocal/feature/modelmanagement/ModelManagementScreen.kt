package com.llmlocal.feature.modelmanagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.llmlocal.feature.modelmanagement.components.FilterBar
import com.llmlocal.feature.modelmanagement.components.ModelCard
import com.llmlocal.feature.modelmanagement.components.ModelDetailSheet
import com.llmlocal.feature.modelmanagement.mvi.ModelManagementIntent
import com.llmlocal.feature.modelmanagement.mvi.ModelManagementState

/**
 * Stateless model-management screen. Receives [state] + [onIntent]; does
 * not know about the ViewModel. Previewable.
 *
 * Layout (top-to-bottom):
 *  1. Section header with a live count of matching models.
 *  2. Intro paragraph explaining what this screen does.
 *  3. [FilterBar] — Status / Family / Size / Quant `FilterChip`s.
 *  4. Catalog list of [ModelCard]s, in the order the view model returned
 *     them. When the filter set is restrictive and the result is empty,
 *     an inline "no matches" message + a "Clear filters" button is shown
 *     instead.
 */
@Composable
fun ModelManagementScreen(
    state: ModelManagementState,
    onIntent: (ModelManagementIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Available models",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = countLabel(state),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "Tap a model to download, view details, or select it for " +
                "recipe generation. Downloads run in the background and " +
                "survive closing the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FilterBar(
            filters = state.filters,
            onFiltersChange = { onIntent(ModelManagementIntent.SetFilters(it)) },
        )

        if (state.filteredCatalog.isEmpty()) {
            EmptyResults(
                onClearFilters = { onIntent(ModelManagementIntent.ClearFilters) },
            )
        } else {
            state.filteredCatalog.forEach { descriptor ->
                ModelCard(
                    descriptor = descriptor,
                    isInstalled = descriptor.id in state.installedIds,
                    isSelected = descriptor.id == state.selectedId,
                    isDownloading = state.downloading.containsKey(descriptor.id),
                    progress = state.downloading[descriptor.id],
                    onIntent = onIntent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    val detailsFor = state.detailsForId?.let { id ->
        state.catalog.firstOrNull { it.id == id }
    }
    if (detailsFor != null) {
        ModelDetailSheet(
            descriptor = detailsFor,
            onDismiss = { onIntent(ModelManagementIntent.DismissDetails) },
        )
    }
}

private fun countLabel(state: ModelManagementState): String {
    val visible = state.filteredCatalog.size
    val total = state.catalog.size
    val installed = state.installedIds.size
    return if (state.filters.isDefault) {
        "$visible of $total models · $installed installed"
    } else {
        "Showing $visible of $total models · $installed installed"
    }
}

@Composable
private fun EmptyResults(onClearFilters: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "No models match your filters",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Try widening the size bucket or clearing the family / " +
                "status filter.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onClearFilters) {
            Text("Clear filters")
        }
    }
}
