package com.llmlocal.feature.modelmanagement.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.llm.download.PerModelProgress
import kotlin.math.roundToInt

/**
 * Linear progress bar + bytes / speed / ETA line used in [ModelCard]
 * while a model is downloading.
 *
 * Adds two visual touches on top of the default M3 indicator:
 *
 *  - The percentage label is rendered in `titleMedium` next to the bar
 *    so the user can see the exact number without squinting at the bar.
 *  - The progress track uses the brand `primary` color rather than the
 *    neutral default — emphasises "this is a download in progress."
 */
@Composable
fun DownloadProgressBar(
    progress: PerModelProgress,
    modifier: Modifier = Modifier,
) {
    val percent = progress.totalBytes?.takeIf { it > 0 }
        ?.let { ((progress.bytesRead * 100) / it).toInt().coerceIn(0, 100) }
        ?: 0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Text(
            text = buildString {
                append(LlmModelManager.humanReadableBytes(progress.bytesRead))
                append(" / ")
                append(progress.totalBytes?.let { LlmModelManager.humanReadableBytes(it) } ?: "?")
                if (progress.speedBytesPerSec > 0) {
                    append(" · ")
                    append(LlmModelManager.humanReadableBytes(progress.speedBytesPerSec))
                    append("/s")
                }
                progress.etaSeconds?.let { secs ->
                    append(" · ")
                    if (secs < 60) append("${secs}s left")
                    else append("${(secs / 60.0).roundToInt()}m left")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}