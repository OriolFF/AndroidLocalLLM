package com.llmlocal.feature.modelmanagement.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.feature.modelmanagement.mvi.ModelManagementIntent

/**
 * A single model row in the catalog list.
 *
 * Visual treatment follows the state of the model — the user must be
 * able to tell at a glance which model is currently in use, which models
 * are downloaded, and which are still just available:
 *
 *  - **Active** (the engine will load this on its next `initialize()`):
 *    thick primary-color border, primary-container background tint, and
 *    a "Active" star badge next to the title.
 *  - **Installed but not active**: tertiary-tinted "Installed" checkmark
 *    badge next to the title. The card has a thin tertiary-tinted border
 *    so the user can scan a list and pick out what's already on disk.
 *  - **Downloading**: linear progress + Cancel button replaces Download.
 *  - **Available**: filled Download button only.
 *  - **Failed**: error footer + a Retry button (Download is shown so the
 *    user can re-attempt without going through the state machine).
 */
@Composable
fun ModelCard(
    descriptor: LlmModelDescriptor,
    isInstalled: Boolean,
    isSelected: Boolean,
    isDownloading: Boolean,
    progress: PerModelProgress?,
    onIntent: (ModelManagementIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (border, containerColor) = when {
        isSelected -> BorderStroke(2.dp, scheme.primary) to scheme.primaryContainer
        isInstalled -> BorderStroke(1.dp, scheme.tertiary.copy(alpha = 0.6f)) to scheme.surface
        else -> null to scheme.surface
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Title row + status badge + info button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = descriptor.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        when {
                            isSelected -> StatusBadge(
                                text = "Active",
                                color = scheme.onPrimaryContainer,
                                container = scheme.primary,
                                icon = Icons.Filled.Star,
                            )
                            isInstalled -> StatusBadge(
                                text = "Installed",
                                color = scheme.tertiary,
                                container = scheme.tertiary.copy(alpha = 0.12f),
                                icon = Icons.Filled.CheckCircle,
                            )
                        }
                    }
                    Text(
                        text = "${LlmModelManager.humanReadableBytes(descriptor.sizeBytes)} · " +
                            "${descriptor.family.name.lowercase().replaceFirstChar { it.uppercase() }} · " +
                            descriptor.license,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onIntent(ModelManagementIntent.ShowDetails(descriptor.id)) }) {
                    Icon(Icons.Outlined.Info, contentDescription = "Details")
                }
            }

            // Description preview
            Text(
                text = descriptor.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )

            // Tag chips
            if (descriptor.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    descriptor.tags.take(4).forEach { tag ->
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(tag) },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }

            // Progress (only when downloading)
            if (isDownloading && progress != null) {
                DownloadProgressBar(progress = progress)
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    isDownloading -> {
                        OutlinedButton(
                            onClick = { onIntent(ModelManagementIntent.CancelDownload(descriptor.id)) },
                        ) {
                            Icon(
                                Icons.Filled.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Cancel")
                        }
                    }
                    isInstalled -> {
                        if (!isSelected) {
                            Button(
                                onClick = { onIntent(ModelManagementIntent.Select(descriptor.id)) },
                            ) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Use this model")
                            }
                        }
                        OutlinedButton(
                            onClick = { onIntent(ModelManagementIntent.Remove(descriptor.id)) },
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Remove")
                        }
                    }
                    else -> {
                        Button(
                            onClick = { onIntent(ModelManagementIntent.StartDownload(descriptor.id)) },
                        ) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Download")
                        }
                    }
                }
            }

            // Failure footer
            progress?.let { p ->
                if (p.state == DownloadState.FAILED) {
                    Text(
                        text = p.failureReason ?: "Download failed — tap Download to retry",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Small pill rendered next to a model title — "Active" (primary fill) or
 * "Installed" (tertiary tint). Sits inline with the title so the badge
 * is the first thing the eye lands on.
 */
@Composable
private fun StatusBadge(
    text: String,
    color: Color,
    container: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
