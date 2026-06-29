package com.llmlocal.feature.recipe.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.GradientHero
import com.llmlocal.core.model.LlmModelDescriptor

/**
 * Banner shown on the recipe screen when no model is selected or the
 * selected model's file is missing on disk. Renders a [GradientHero] with
 * a single "Browse models" CTA that opens the model management screen.
 *
 * The copy adapts: if the user has *selected* a model that just isn't
 * installed yet, we tell them which one. Otherwise we explain the
 * general "install an LLM to get started" path.
 */
@Composable
fun NoModelAvailableBanner(
    selectedModel: LlmModelDescriptor?,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, subtitle) = if (selectedModel != null) {
        "Model not installed" to "'${selectedModel.displayName}' is selected but its file isn't on this device yet."
    } else {
        "No LLM on this device" to "Pick and download a model to start generating recipes locally."
    }
    GradientHero(
        title = title,
        subtitle = subtitle,
        icon = Icons.Outlined.Memory,
        actionLabel = "Browse models",
        onAction = onOpenModels,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
