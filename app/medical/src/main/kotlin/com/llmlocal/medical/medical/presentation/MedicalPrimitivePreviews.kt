package com.llmlocal.medical.medical.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.NumberBadge
import com.llmlocal.core.designsystem.component.PhaseItem
import com.llmlocal.core.designsystem.component.PhaseProgress
import com.llmlocal.core.designsystem.component.PhaseStatus
import com.llmlocal.core.designsystem.component.SectionHeader
import com.llmlocal.core.designsystem.component.SkeletonLine
import com.llmlocal.core.designsystem.component.StreamingCursor
import com.llmlocal.core.designsystem.component.StreamingText
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.medical.config.rememberMedicalLabels
import com.llmlocal.medical.designsystem.MedicalTokens

/**
 * Gallery of the shared `:core:designsystem` primitives rendered with
 * [MedicalTokens], so a change to a primitive can be checked against this
 * brand without launching the app. The recipes app has the same gallery
 * under its own tokens.
 */
@Composable
private fun PrimitiveGallery(darkTheme: Boolean) {
    val labels = rememberMedicalLabels()
    AppTheme(tokens = MedicalTokens, darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SectionHeader(label = "PhaseProgress", icon = Icons.Outlined.Info)
                PhaseProgress(
                    items = listOf(
                        PhaseItem("Reading page", PhaseStatus.Done, statusDescription = "Done"),
                        PhaseItem(
                            label = "Extracting",
                            status = PhaseStatus.Active,
                            detail = "Running on your phone — this takes a moment",
                            statusDescription = "In progress",
                        ),
                        PhaseItem("Saving pictures", PhaseStatus.Warning, detail = "Pictures unavailable"),
                        PhaseItem("Saving", PhaseStatus.Failed, detail = "Could not write to storage"),
                        PhaseItem("Done", PhaseStatus.Pending),
                    ),
                )

                SectionHeader(
                    label = "SectionHeader with tint",
                    icon = Icons.Outlined.WarningAmber,
                    iconTint = MaterialTheme.colorScheme.error,
                )
                SectionHeader(label = "SectionHeader without icon", icon = null)

                SectionHeader(label = "StreamingText — warming up", icon = null)
                StreamingText(
                    text = "",
                    isGenerating = true,
                    warmingUpLabel = labels.warmingUpLabel,
                )
                SectionHeader(label = "StreamingText — streaming", icon = null)
                StreamingText(
                    text = "The model has produced this much text so far and is still",
                    isGenerating = true,
                    warmingUpLabel = labels.warmingUpLabel,
                )
                SectionHeader(label = "StreamingText — final", icon = null)
                StreamingText(
                    text = "Generation finished; the caret is gone and the text is at full opacity.",
                    isGenerating = false,
                    warmingUpLabel = labels.warmingUpLabel,
                )

                SectionHeader(label = "SkeletonLine + StreamingCursor", icon = null)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonLine(widthFraction = 0.6f, height = 24.dp)
                    SkeletonLine(widthFraction = 1f)
                    SkeletonLine(widthFraction = 0.85f)
                    SkeletonLine(widthFraction = 0.4f)
                    StreamingCursor()
                }

                SectionHeader(label = "NumberBadge", icon = null)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { NumberBadge(n = it) }
                    NumberBadge(n = 12)
                }
                Text(
                    text = "Body text for contrast reference.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "Medical / Primitives", showBackground = true, heightDp = 1100)
@Composable
private fun MedicalPrimitivesPreview() {
    PrimitiveGallery(darkTheme = false)
}

@Preview(name = "Medical / Primitives (Dark)", showBackground = true, heightDp = 1100)
@Composable
private fun MedicalPrimitivesDarkPreview() {
    PrimitiveGallery(darkTheme = true)
}
