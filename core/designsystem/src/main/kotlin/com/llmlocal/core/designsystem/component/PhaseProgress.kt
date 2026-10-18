package com.llmlocal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.theme.designTokens

/**
 * Vertical list of pipeline phases, each with a status marker, a label and
 * an optional detail line. Rows are joined by a thin connector so a glance
 * shows "two done, one running, one pending" without reading the labels.
 *
 * Markers:
 *  - [PhaseStatus.Pending] — hollow `outlineVariant` ring, muted label
 *  - [PhaseStatus.Active]  — indeterminate primary ring
 *  - [PhaseStatus.Done]    — check circle in `designTokens.successColor`
 *  - [PhaseStatus.Failed]  — error outline in `colorScheme.error`
 *  - [PhaseStatus.Warning] — warning triangle in `colorScheme.tertiary`
 *
 * Brand-free: only `MaterialTheme` slots and `designTokens.successColor`.
 * Copy is the caller's; the component renders exactly what it is given.
 *
 * @param items ordered phases, first to last.
 */
@Composable
fun PhaseProgress(
    items: List<PhaseItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            PhaseRow(item = item, isLast = index == items.lastIndex)
        }
    }
}

/**
 * One phase in a [PhaseProgress].
 *
 * @param label the phase name.
 * @param status visual treatment, see [PhaseStatus].
 * @param detail optional secondary line under the label (a hint, an
 *   elapsed-time counter, a partial-failure note).
 * @param statusDescription optional TalkBack text for the status
 *   ("Done", "In progress", …). Supplied by the caller because the
 *   component carries no copy of its own.
 */
@Immutable
data class PhaseItem(
    val label: String,
    val status: PhaseStatus,
    val detail: String? = null,
    val statusDescription: String? = null,
)

/** Visual state of a [PhaseItem]. */
enum class PhaseStatus { Pending, Active, Done, Failed, Warning }

private val MarkerSize = 20.dp
private val MarkerColumnWidth = 24.dp
private val ConnectorHeight = 16.dp

@Composable
private fun PhaseRow(
    item: PhaseItem,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                item.statusDescription?.let { stateDescription = it }
            },
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(MarkerColumnWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PhaseMarker(item.status)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(ConnectorHeight)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.padding(bottom = if (isLast) 0.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = when (item.status) {
                    PhaseStatus.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (item.detail != null) {
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhaseMarker(status: PhaseStatus) {
    Box(
        modifier = Modifier.size(MarkerSize),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            PhaseStatus.Pending -> Box(
                modifier = Modifier
                    .size(MarkerSize)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            PhaseStatus.Active -> CircularProgressIndicator(
                modifier = Modifier.size(MarkerSize),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            PhaseStatus.Done -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = designTokens.successColor,
                modifier = Modifier.size(MarkerSize),
            )
            PhaseStatus.Failed -> Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(MarkerSize),
            )
            PhaseStatus.Warning -> Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(MarkerSize),
            )
        }
    }
}
