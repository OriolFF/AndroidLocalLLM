package com.llmlocal.feature.modelmanagement.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.core.llm.download.LlmModelManager
import com.llmlocal.core.llm.download.PerModelProgress
import kotlin.math.roundToInt

/**
 * Linear progress bar + bytes / speed / ETA line used in [ModelCard]
 * while a model is downloading.
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}