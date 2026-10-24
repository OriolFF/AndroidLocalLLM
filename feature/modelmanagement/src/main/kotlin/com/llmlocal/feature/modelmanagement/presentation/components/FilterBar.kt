package com.llmlocal.feature.modelmanagement.presentation.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llmlocal.core.model.ModelAccelerator
import com.llmlocal.core.model.ModelFamily
import com.llmlocal.feature.modelmanagement.R
import com.llmlocal.feature.modelmanagement.domain.ModelFilters
import com.llmlocal.feature.modelmanagement.domain.QuantFilter
import com.llmlocal.feature.modelmanagement.domain.SizeBucket
import com.llmlocal.feature.modelmanagement.domain.StatusFilter
import com.llmlocal.feature.modelmanagement.presentation.mvi.modelManagementLabels

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
    val labels = modelManagementLabels
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
                    text = labels.filterByLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = { onFiltersChange(ModelFilters.DEFAULT) },
                    enabled = !filters.isDefault,
                ) {
                    Text(labels.clearFiltersAction)
                }
            }

            // Status (enum)
            ChipRow(label = labels.filterStatusLabel) {
                StatusFilter.entries.forEach { opt ->
                    val icon = when (opt) {
                        StatusFilter.ALL -> Icons.Outlined.AllInclusive
                        StatusFilter.INSTALLED -> Icons.Outlined.CheckCircle
                        StatusFilter.AVAILABLE -> Icons.Outlined.DoneAll
                        StatusFilter.DOWNLOADING -> Icons.Outlined.CloudDownload
                    }
                    StatusChip(
                        label = opt.label(),
                        selected = filters.status == opt,
                        icon = icon,
                        onClick = { onFiltersChange(filters.copy(status = opt)) },
                    )
                }
            }

            // Family (nullable)
            ChipRow(label = labels.filterFamilyLabel) {
                StatusChip(
                    label = labels.filterAllLabel,
                    selected = filters.family == null,
                    onClick = { onFiltersChange(filters.copy(family = null)) },
                )
                ModelFamily.entries.forEach { fam ->
                    StatusChip(
                        label = fam.label(),
                        selected = filters.family == fam,
                        onClick = { onFiltersChange(filters.copy(family = fam)) },
                    )
                }
            }

            // Size (nullable)
            ChipRow(label = labels.filterSizeLabel) {
                StatusChip(
                    label = labels.filterAllLabel,
                    selected = filters.sizeBucket == null,
                    onClick = { onFiltersChange(filters.copy(sizeBucket = null)) },
                )
                SizeBucket.entries.forEach { bucket ->
                    StatusChip(
                        label = bucket.label(),
                        selected = filters.sizeBucket == bucket,
                        onClick = { onFiltersChange(filters.copy(sizeBucket = bucket)) },
                    )
                }
            }

            // Quant (enum)
            ChipRow(label = labels.filterQuantLabel) {
                QuantFilter.entries.forEach { opt ->
                    StatusChip(
                        label = opt.label(),
                        selected = filters.quant == opt,
                        onClick = { onFiltersChange(filters.copy(quant = opt)) },
                    )
                }
            }
        }
    }
}

/** Display name used in the filter chips, the model card and the detail sheet. */
/** Short accelerator name for spec rows and the detail sheet. */
@Composable
fun ModelAccelerator.label(): String = stringResource(
    when (this) {
        ModelAccelerator.CPU -> R.string.modelmanagement_accelerator_cpu
        ModelAccelerator.GPU -> R.string.modelmanagement_accelerator_gpu
        ModelAccelerator.NPU -> R.string.modelmanagement_accelerator_npu
    },
)

@Composable
fun ModelFamily.label(): String = stringResource(
    when (this) {
        ModelFamily.GENERAL -> R.string.modelmanagement_family_general
        ModelFamily.CHAT -> R.string.modelmanagement_family_chat
        ModelFamily.INSTRUCT -> R.string.modelmanagement_family_instruct
        ModelFamily.CODE -> R.string.modelmanagement_family_code
        ModelFamily.MULTIMODAL -> R.string.modelmanagement_family_multimodal
    },
)

@Composable
private fun StatusFilter.label(): String = stringResource(
    when (this) {
        StatusFilter.ALL -> R.string.modelmanagement_status_all
        StatusFilter.INSTALLED -> R.string.modelmanagement_status_installed
        StatusFilter.AVAILABLE -> R.string.modelmanagement_status_available
        StatusFilter.DOWNLOADING -> R.string.modelmanagement_status_downloading
    },
)

@Composable
private fun SizeBucket.label(): String = stringResource(
    when (this) {
        SizeBucket.COMPACT -> R.string.modelmanagement_size_compact
        SizeBucket.SMALL -> R.string.modelmanagement_size_small
        SizeBucket.FULL -> R.string.modelmanagement_size_full
    },
)

@Composable
private fun QuantFilter.label(): String = stringResource(
    when (this) {
        QuantFilter.ALL -> R.string.modelmanagement_quant_all
        QuantFilter.QUANTISED -> R.string.modelmanagement_quant_quantised
        QuantFilter.FULL_PRECISION -> R.string.modelmanagement_quant_full_precision
    },
)

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
