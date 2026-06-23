package com.llmlocal.feature.modelmanagement.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llmlocal.core.model.ModelFamily
import com.llmlocal.feature.modelmanagement.mvi.ModelFilters
import com.llmlocal.feature.modelmanagement.mvi.QuantFilter
import com.llmlocal.feature.modelmanagement.mvi.SizeBucket
import com.llmlocal.feature.modelmanagement.mvi.StatusFilter

/**
 * Sticky-feeling filter bar for the model catalog. Renders three rows of
 * [FilterChip]s, one per filter dimension:
 *
 *  - **Status** — All / Installed / Available / Downloading.
 *  - **Family** — All / General / Chat / Instruct / Code / Multimodal.
 *  - **Size**   — All / Compact (< 1 GB) / Small (1–2 GB) / Full (> 2 GB).
 *  - **Quant**  — All / Quantised / Full precision.
 *
 * Each row carries a small label on the leading edge so the categories
 * stay obvious when the chips wrap. A "Clear" button in the header
 * resets to [ModelFilters.DEFAULT] and is only enabled when at least one
 * filter is non-default.
 *
 * The bar is stateless — it receives the current [filters] and emits
 * updates via [onFiltersChange]. The view model owns the source of truth.
 */
@Composable
fun FilterBar(
    filters: ModelFilters,
    onFiltersChange: (ModelFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Filter by",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = { onFiltersChange(ModelFilters.DEFAULT) },
                    enabled = !filters.isDefault,
                ) {
                    Text("Clear")
                }
            }

            // Status
            ChipRow(label = "Status") {
                StatusFilter.entries.forEach { opt ->
                    FilterChip(
                        selected = filters.status == opt,
                        onClick = { onFiltersChange(filters.copy(status = opt)) },
                        label = { Text(opt.displayName) },
                    )
                }
            }

            // Family
            ChipRow(label = "Family") {
                FilterChip(
                    selected = filters.family == null,
                    onClick = { onFiltersChange(filters.copy(family = null)) },
                    label = { Text("All") },
                )
                ModelFamily.entries.forEach { fam ->
                    FilterChip(
                        selected = filters.family == fam,
                        onClick = { onFiltersChange(filters.copy(family = fam)) },
                        label = { Text(fam.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            // Size
            ChipRow(label = "Size") {
                FilterChip(
                    selected = filters.sizeBucket == null,
                    onClick = { onFiltersChange(filters.copy(sizeBucket = null)) },
                    label = { Text("All") },
                )
                SizeBucket.entries.forEach { bucket ->
                    FilterChip(
                        selected = filters.sizeBucket == bucket,
                        onClick = { onFiltersChange(filters.copy(sizeBucket = bucket)) },
                        label = { Text(bucket.displayName) },
                    )
                }
            }

            // Quant
            ChipRow(label = "Quant") {
                QuantFilter.entries.forEach { opt ->
                    FilterChip(
                        selected = filters.quant == opt,
                        onClick = { onFiltersChange(filters.copy(quant = opt)) },
                        label = { Text(opt.displayName) },
                    )
                }
            }
        }
    }
}

/**
 * Internal helper: a single labelled row of [FilterChip]s. The chips
 * scroll horizontally on overflow so the row stays one line tall.
 */
@Composable
private fun ChipRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}
