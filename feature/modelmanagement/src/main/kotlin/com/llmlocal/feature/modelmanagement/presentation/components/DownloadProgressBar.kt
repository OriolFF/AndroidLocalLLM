package com.llmlocal.feature.modelmanagement.presentation.components

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.feature.modelmanagement.R
import com.llmlocal.feature.modelmanagement.domain.DownloadProgress
import com.llmlocal.feature.modelmanagement.domain.DownloadStatus
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
    progress: DownloadProgress,
    modifier: Modifier = Modifier,
) {
    val percent = progress.totalBytes?.takeIf { it > 0 }
        ?.let { ((progress.bytesRead * 100) / it).toInt().coerceIn(0, 100) }
        ?: 0

    val resources = LocalContext.current.resources
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
                append(formatBytes(progress.bytesRead))
                append(" / ")
                append(progress.totalBytes?.let { formatBytes(it) } ?: "?")
                if (progress.speedBytesPerSec > 0) {
                    append(" · ")
                    append(formatBytes(progress.speedBytesPerSec))
                    append("/s")
                }
                progress.etaSeconds?.let { secs ->
                    append(" · ")
                    if (secs < 60) append(resources.getString(R.string.modelmanagement_progress_seconds_left, secs.toInt()))
                    else append(resources.getString(R.string.modelmanagement_progress_minutes_left, (secs / 60.0).roundToInt()))
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
/**
 * Overload for callers that hold the `:core:llm` snapshot directly (the
 * Recipe Keeper library banner, via `ModelReadiness.Downloading`).
 */
@Composable
fun DownloadProgressBar(
    progress: PerModelProgress,
    modifier: Modifier = Modifier,
) {
    DownloadProgressBar(
        progress = DownloadProgress(
            modelId = progress.modelId,
            status = when (progress.state) {
                DownloadState.IDLE -> DownloadStatus.IDLE
                DownloadState.RUNNING -> DownloadStatus.RUNNING
                DownloadState.SUCCEEDED -> DownloadStatus.SUCCEEDED
                DownloadState.FAILED -> DownloadStatus.FAILED
                DownloadState.CANCELLED -> DownloadStatus.CANCELLED
            },
            bytesRead = progress.bytesRead,
            totalBytes = progress.totalBytes,
            speedBytesPerSec = progress.speedBytesPerSec,
            etaSeconds = progress.etaSeconds,
            failureReason = progress.failureReason,
        ),
        modifier = modifier,
    )
}
