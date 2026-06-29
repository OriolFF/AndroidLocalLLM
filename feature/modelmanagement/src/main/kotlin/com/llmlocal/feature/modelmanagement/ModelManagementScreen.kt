package com.llmlocal.feature.modelmanagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.EmptyState
import com.llmlocal.feature.modelmanagement.components.FilterBar
import com.llmlocal.feature.modelmanagement.components.ModelCard
import com.llmlocal.feature.modelmanagement.components.ModelDetailSheet
import com.llmlocal.feature.modelmanagement.mvi.ModelManagementIntent
import com.llmlocal.feature.modelmanagement.mvi.ModelManagementState

/**
 * Stateless model-management screen. Receives [state] + [onIntent]; does
 * not know about the ViewModel. Previewable.
 *
 * Layout:
 *  1. **Header** — title + count label.
 *  2. **Intro card** — short tonal `Surface` explaining the screen.
 *  3. **[FilterBar]** — sticky-looking filter chips.
 *  4. **Catalog** — a `LazyColumn` of [ModelCard]s, with [EmptyState]
 *     inserted when the filter set yields zero results.
 *
 * A [ModelDetailSheet] is rendered at the bottom when `detailsForId` is
 * non-null.
 */
@Composable
fun ModelManagementScreen(
    state: ModelManagementState,
    onIntent: (ModelManagementIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") { Header(state = state) }
        item(key = "intro") { IntroCard() }
        item(key = "filters") {
            FilterBar(
                filters = state.filters,
                onFiltersChange = { onIntent(ModelManagementIntent.SetFilters(it)) },
            )
        }

        if (state.filteredCatalog.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "No models match your filters",
                    message = "Try widening the size bucket or clearing the " +
                        "family / status filter.",
                    actionLabel = "Clear filters",
                    onAction = { onIntent(ModelManagementIntent.ClearFilters) },
                )
            }
        } else {
            items(
                items = state.filteredCatalog,
                key = { it.id },
            ) { descriptor ->
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

@Composable
private fun Header(state: ModelManagementState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Available models",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = countLabel(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IntroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = "Tap a model to download, view details, or select it for " +
                "recipe generation. Downloads run in the background and " +
                "survive closing the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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