package com.llmlocal.feature.recipe.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.feature.recipe.mvi.ModelStatus
import kotlin.math.roundToInt

/**
 * Banner shown when the on-device model is not yet available, or while a
 * download / engine init is in progress. Hidden when the model is [ModelStatus.Ready].
 *
 * Always shows a progress bar while the download is active, plus
 * human-readable bytes / total and a rolling speed and ETA.
 */
@Composable
fun ModelDownloadBanner(
    status: ModelStatus,
    modelSizeText: String,
    modelFilename: String,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status is ModelStatus.Ready) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---- Title + supporting text -------------------------------
        val (title, supporting) = titleAndSupporting(status, modelSizeText, modelFilename)
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        if (supporting.isNotEmpty()) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Progress bar (visible for any active phase) ----------
        val showProgress = status is ModelStatus.Downloading ||
            status is ModelStatus.DownloadProgress ||
            status is ModelStatus.Checking
        AnimatedVisibility(visible = showProgress) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                when (status) {
                    is ModelStatus.DownloadProgress -> {
                        val progress = if (status.percent in 0..100) {
                            status.percent / 100f
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        )
                    }
                    else -> {
                        // Indeterminate for Checking / early Downloading.
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        // ---- Bytes / speed / ETA -----------------------------------
        if (status is ModelStatus.DownloadProgress) {
            DownloadStatsRow(status)
        }

        // ---- Action buttons ---------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (status) {
                is ModelStatus.Failed -> {
                    OutlinedButton(onClick = onDownload) { Text("Retry") }
                    Button(onClick = onDownload) { Text("Download model") }
                }
                is ModelStatus.Checking -> {
                    // No action while checking.
                }
                is ModelStatus.Downloading, is ModelStatus.DownloadProgress -> {
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
                is ModelStatus.Unknown, is ModelStatus.NotSelected -> {
                    Button(onClick = onDownload) {
                        Text(text = "Download model${if (modelSizeText.isNotEmpty()) " ($modelSizeText)" else ""}")
                    }
                }
                is ModelStatus.Ready -> Unit // banner is hidden anyway
            }
        }
    }
}

@Composable
private fun DownloadStatsRow(status: ModelStatus.DownloadProgress) {
    val read = com.llmlocal.core.llm.download.LlmModelManager
        .humanReadableBytes(status.bytesRead)
    val total = status.totalBytes
        ?.let { com.llmlocal.core.llm.download.LlmModelManager.humanReadableBytes(it) }
        ?: "?"
    val speed = if (status.speedBytesPerSec > 0) {
        " · " + com.llmlocal.core.llm.download.LlmModelManager
            .humanReadableBytes(status.speedBytesPerSec) + "/s"
    } else ""
    val eta = status.etaSeconds?.let { secs ->
        if (secs < 60) " · ${secs}s left"
        else " · ${(secs / 60.0).roundToInt()}m left"
    } ?: ""
    Text(
        text = "$read / $total$speed$eta",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun titleAndSupporting(
    status: ModelStatus,
    modelSizeText: String,
    modelFilename: String,
): Pair<String, String> = when (status) {
    is ModelStatus.Unknown -> "Download model" to
        "One-time setup — about $modelSizeText. The model runs entirely on this device."
    is ModelStatus.Checking -> "Preparing…" to "Checking the on-device model."
    is ModelStatus.Downloading -> "Starting download…" to
        "Saving $modelFilename to internal storage."
    is ModelStatus.DownloadProgress -> "Downloading model" to
        "About $modelSizeText — keep the app open."
    is ModelStatus.Ready -> "" to ""
    is ModelStatus.Failed -> "Model unavailable" to status.reason
    is ModelStatus.NotSelected -> "Real LLM not loaded" to
        "Switch off Demo mode in the toggle to download the on-device model."
}
