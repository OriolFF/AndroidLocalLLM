package com.llmlocal.feature.modelmanagement.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.EmptyState
import com.llmlocal.feature.modelmanagement.domain.ModelFilters
import com.llmlocal.feature.modelmanagement.presentation.components.FilterBar
import com.llmlocal.feature.modelmanagement.presentation.components.ModelCard
import com.llmlocal.feature.modelmanagement.presentation.components.ModelDetailSheet
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementIntent
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementState
import com.llmlocal.feature.modelmanagement.presentation.mvi.modelManagementLabels

/**
 * Stateless model-management screen. Receives [state] + [onIntent]; does
 * not know about the ViewModel. Previewable.
 *
 * Reads [modelManagementLabels] for all user-visible strings.
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
    val labels = modelManagementLabels
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") { Header(state = state, onIntent = onIntent) }
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
                    title = labels.emptyTitle,
                    message = labels.emptyMessage,
                    actionLabel = labels.emptyActionLabel,
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
private fun Header(state: ModelManagementState, onIntent: (ModelManagementIntent) -> Unit) {
    val labels = modelManagementLabels
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = labels.screenTitle,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = countLabel(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Discovery: pulls the latest LiteRT-LM bundles from the remote source.
        if (state.discovering) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = labels.discoveringLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            TextButton(
                onClick = { onIntent(ModelManagementIntent.DiscoverModels) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = labels.discoverAction, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun IntroCard() {
    val labels = modelManagementLabels
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = labels.introText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun countLabel(state: ModelManagementState): String {
    val labels = modelManagementLabels
    val visible = state.filteredCatalog.size
    val total = state.catalog.size
    val installed = state.installedIds.size
    return if (state.filters.isDefault) {
        labels.countLabelDefault(visible, total, installed)
    } else {
        labels.countLabelFiltered(visible, total, installed)
    }
}