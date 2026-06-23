package com.llmlocal.feature.modelmanagement.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.model.LlmModelDescriptor

/**
 * Bottom sheet shown when the user taps the info icon on a [ModelCard].
 * Carries the full description, tags, license, author, and the raw
 * download URL — useful for debugging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDetailSheet(
    descriptor: LlmModelDescriptor,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = descriptor.displayName,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "by ${descriptor.author} · ${descriptor.license}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = descriptor.description,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                descriptor.tags.forEach { tag ->
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

            DetailRow("Size", LlmModelManager.humanReadableBytes(descriptor.sizeBytes))
            DetailRow("Family", descriptor.family.name.lowercase().replaceFirstChar { it.uppercase() })
            DetailRow("Min RAM", "${descriptor.minRamMb} MB")
            DetailRow("File", descriptor.filename)
            DetailRow("URL", descriptor.url)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}