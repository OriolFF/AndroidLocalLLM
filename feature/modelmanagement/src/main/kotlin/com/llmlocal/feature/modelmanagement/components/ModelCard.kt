package com.llmlocal.feature.modelmanagement.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.StatusDot
import com.llmlocal.core.designsystem.component.StatusTone
import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.core.model.ModelFamily
import com.llmlocal.feature.modelmanagement.mvi.ModelManagementIntent
import com.llmlocal.feature.modelmanagement.mvi.modelManagementLabels

/**
 * A single model row in the catalog list.
 *
 * Reads [modelManagementLabels] for all user-visible strings.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val labels = modelManagementLabels
    val scheme = MaterialTheme.colorScheme
    // "Active" means the engine can actually use this model: selected *and*
    // installed. On a fresh install the catalog default is already selected,
    // and badging it Active next to a Download button contradicted the
    // app's own "choose a model to start" hero (device-QA finding 5). A
    // selected-but-missing model renders as a plain card with Download.
    val isActive = isSelected && isInstalled
    val containerBrush: Brush? = if (isActive) {
        Brush.verticalGradient(
            colors = listOf(
                scheme.primaryContainer,
                scheme.secondaryContainer,
            ),
        )
    } else {
        null
    }
    val containerColor = if (isActive) {
        scheme.primaryContainer.copy(alpha = 0.45f)
    } else if (isInstalled) {
        scheme.tertiaryContainer.copy(alpha = 0.30f)
    } else {
        scheme.surface
    }
    val tonalElevation = if (isActive) 3.dp else 1.dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = tonalElevation,
        color = containerColor,
    ) {
        val innerModifier = if (containerBrush != null) {
            Modifier
                .fillMaxWidth()
                .background(containerBrush)
        } else {
            Modifier.fillMaxWidth()
        }
        Box(modifier = innerModifier) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Title row + status badge + info button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FamilyIcon(family = descriptor.family, isActive = isActive)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // `fill = false` lets the badge keep its intrinsic width even
                            // with a wide serif title; the title wraps to two lines instead
                            // of squeezing the badge into one letter per line.
                            Text(
                                text = descriptor.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                color = if (isActive) scheme.onPrimaryContainer else scheme.onSurface,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            when {
                                isActive -> StatusDot(
                                    label = labels.activeBadge,
                                    icon = Icons.Filled.Star,
                                    tone = StatusTone.Active,
                                )
                                isInstalled -> StatusDot(
                                    label = labels.installedBadge,
                                    icon = Icons.Filled.CheckCircle,
                                    tone = StatusTone.Installed,
                                )
                            }
                        }
                        Text(
                            text = "${LlmModelManager.humanReadableBytes(descriptor.sizeBytes)} · " +
                                "${descriptor.family.displayName} · ${descriptor.license}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActive) {
                                scheme.onPrimaryContainer.copy(alpha = 0.78f)
                            } else {
                                scheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { onIntent(ModelManagementIntent.ShowDetails(descriptor.id)) }) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = labels.detailsActionDescription,
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }

                // Description preview
                Text(
                    text = descriptor.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 3,
                )

                // Tag chips
                if (descriptor.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        descriptor.tags.take(6).forEach { tag ->
                            TagPill(label = tag)
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
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Icon(
                                    Icons.Filled.Cancel,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = labels.cancelDownloadLabel,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                        isInstalled -> {
                            if (!isSelected) {
                                Button(
                                    onClick = { onIntent(ModelManagementIntent.Select(descriptor.id)) },
                                    shape = MaterialTheme.shapes.large,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = scheme.primary,
                                        contentColor = scheme.onPrimary,
                                    ),
                                ) {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = labels.useModelLabel,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { onIntent(ModelManagementIntent.Remove(descriptor.id)) },
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = labels.removeLabel,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                        else -> {
                            Button(
                                onClick = { onIntent(ModelManagementIntent.StartDownload(descriptor.id)) },
                                shape = MaterialTheme.shapes.large,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                ),
                            ) {
                                Icon(
                                    Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = labels.downloadLabel,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                }

                // Failure footer
                progress?.let { p ->
                    if (p.state == DownloadState.FAILED) {
                        Text(
                            text = p.failureReason ?: labels.failureFooterFallback,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Leading tinted circle with the family-appropriate icon. Active cards
 * use the primary container; passive cards use tertiary container.
 */
@Composable
private fun FamilyIcon(family: ModelFamily, isActive: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val container = if (isActive) scheme.primary else scheme.tertiaryContainer
    val onContainer = if (isActive) scheme.onPrimary else scheme.onTertiaryContainer
    val icon: ImageVector = when (family) {
        ModelFamily.GENERAL -> Icons.Outlined.Psychology
        ModelFamily.CHAT -> Icons.Outlined.ChatBubbleOutline
        ModelFamily.INSTRUCT -> Icons.Outlined.School
        ModelFamily.CODE -> Icons.Outlined.Code
        ModelFamily.MULTIMODAL -> Icons.Outlined.Image
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Small pill for model tags — neutral, low-contrast, fits in a chip row. */
@Composable
private fun TagPill(label: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .background(scheme.surfaceVariant, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}