package com.llmlocal.feature.modelmanagement.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llmlocal.core.model.ModelFamily
import com.llmlocal.feature.modelmanagement.mvi.ModelFilters
import com.llmlocal.feature.modelmanagement.mvi.QuantFilter
import com.llmlocal.feature.modelmanagement.mvi.SizeBucket
import com.llmlocal.feature.modelmanagement.mvi.StatusFilter

/**
 * Sticky-feeling filter bar for the model catalog. Renders four vertical
 * rows of [FilterChip]s, one per filter dimension, each row labelled on
 * the leading edge:
 *
 *  - **Status** — All / Installed / Available / Downloading.
 *  - **Family** — All / General / Chat / Instruct / Code / Multimodal.
 *  - **Size**   — All / Compact / Small / Full.
 *  - **Quant**  — All / Quantised / Full precision.
 *
 * A "Clear" button in the header resets to [ModelFilters.DEFAULT] and is
 * only enabled when at least one filter is non-default. The bar is
 * stateless — receives the current [filters] and emits updates via
 * [onFiltersChange].
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
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Filter by",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = { onFiltersChange(ModelFilters.DEFAULT) },
                    enabled = !filters.isDefault,
                ) {
                    Text("Clear")
                }
            }

            // Status (enum)
            ChipRow(label = "Status") {
                StatusFilter.entries.forEach { opt ->
                    val icon = when (opt) {
                        StatusFilter.ALL -> Icons.Outlined.AllInclusive
                        StatusFilter.INSTALLED -> Icons.Outlined.CheckCircle
                        StatusFilter.AVAILABLE -> Icons.Outlined.DoneAll
                        StatusFilter.DOWNLOADING -> Icons.Outlined.CloudDownload
                    }
                    StatusChip(
                        label = opt.displayName,
                        selected = filters.status == opt,
                        icon = icon,
                        onClick = { onFiltersChange(filters.copy(status = opt)) },
                    )
                }
            }

            // Family (nullable)
            ChipRow(label = "Family") {
                StatusChip(
                    label = "All",
                    selected = filters.family == null,
                    onClick = { onFiltersChange(filters.copy(family = null)) },
                )
                ModelFamily.entries.forEach { fam ->
                    StatusChip(
                        label = fam.displayName,
                        selected = filters.family == fam,
                        onClick = { onFiltersChange(filters.copy(family = fam)) },
                    )
                }
            }

            // Size (nullable)
            ChipRow(label = "Size") {
                StatusChip(
                    label = "All",
                    selected = filters.sizeBucket == null,
                    onClick = { onFiltersChange(filters.copy(sizeBucket = null)) },
                )
                SizeBucket.entries.forEach { bucket ->
                    StatusChip(
                        label = bucket.displayName,
                        selected = filters.sizeBucket == bucket,
                        onClick = { onFiltersChange(filters.copy(sizeBucket = bucket)) },
                    )
                }
            }

            // Quant (enum)
            ChipRow(label = "Quant") {
                QuantFilter.entries.forEach { opt ->
                    StatusChip(
                        label = opt.displayName,
                        selected = filters.quant == opt,
                        onClick = { onFiltersChange(filters.copy(quant = opt)) },
                    )
                }
            }
        }
    }
}

/** Display name used in the filter chips. */
val ModelFamily.displayName: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

/**
 * Single labelled row of chips. The label renders as a `titleSmall`
 * leading label inside the row, then chips are horizontally scrollable
 * when they overflow.
 */
@Composable
private fun ChipRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        leadingIcon = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else null,
        shape = MaterialTheme.shapes.small,
    )
}
