package com.llmlocal.feature.llmtask.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.GradientHero
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.feature.llmtask.mvi.LlmTaskLabels

/**
 * Banner shown on the LLM-task screen when no model is selected or the
 * selected model's file is missing on disk. Renders a [GradientHero] with
 * a single CTA that opens the model management screen.
 *
 * The copy adapts: if the user has *selected* a model that just isn't
 * installed yet, we tell them which one. Otherwise we explain the
 * general "install an LLM to get started" path.
 *
 * @param icon override the default Memory icon (e.g. a recipe app could
 *   pass [androidx.compose.material.icons.outlined.Restaurant]).
 */
@Composable
fun NoModelAvailableBanner(
    selectedModel: LlmModelDescriptor?,
    labels: LlmTaskLabels,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Memory,
) {
    val (title, subtitle) = if (selectedModel != null) {
        labels.noModelSelectedTitle to labels.noModelSelectedSubtitle(selectedModel.displayName)
    } else {
        labels.noModelGenericTitle to labels.noModelGenericSubtitle
    }
    GradientHero(
        title = title,
        subtitle = subtitle,
        icon = icon,
        actionLabel = labels.browseModelsLabel,
        onAction = onOpenModels,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}