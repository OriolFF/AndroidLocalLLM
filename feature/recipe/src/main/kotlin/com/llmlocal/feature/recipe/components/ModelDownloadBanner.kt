package com.llmlocal.feature.recipe.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.feature.recipe.mvi.ModelStatus

/**
 * Banner shown when the on-device model is not yet available, or while a
 * download / engine init is in progress.
 */
@Composable
fun ModelDownloadBanner(
    status: ModelStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, supporting, showProgress, showRetry) = when (status) {
        is ModelStatus.Unknown -> Quad("Preparing…", "Checking the on-device model.", false, false)
        is ModelStatus.Checking -> Quad("Preparing…", "Checking the on-device model.", false, false)
        is ModelStatus.Downloading -> Quad("Downloading model", "This is a one-time setup.", false, false)
        is ModelStatus.DownloadProgress -> Quad(
            "Downloading model",
            "${status.percent}% complete",
            true,
            false,
        )
        is ModelStatus.Ready -> return  // No banner
        is ModelStatus.Failed -> Quad(
            "Model unavailable",
            status.reason,
            false,
            true,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (showRetry) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onRetry) {
                    Text(text = "Download model")
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
