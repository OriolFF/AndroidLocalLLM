package com.llmlocal.recipekeeper.previews

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.recipekeeper.designsystem.RecipeKeeperTokens
import com.llmlocal.recipekeeper.output.ClipDraftCard

/**
 * `@Preview`s for the streamed draft card: all-skeleton (no tokens yet),
 * half-filled with a dimmed tail, and complete — light and dark.
 */
@Composable
private fun DraftPreviewSurface(streamedText: String, isRunning: Boolean, darkTheme: Boolean = false) {
    AppTheme(tokens = RecipeKeeperTokens, darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            ClipDraftCard(
                streamedText = streamedText,
                isRunning = isRunning,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(name = "Draft · waiting", showBackground = true)
@Composable
private fun DraftWaitingPreview() = DraftPreviewSurface(streamedText = "", isRunning = true)

@Preview(name = "Draft · streaming", showBackground = true)
@Composable
private fun DraftStreamingPreview() = DraftPreviewSurface(SampleData.streamedPartial, isRunning = true)

@Preview(name = "Draft · streaming · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DraftStreamingDarkPreview() = DraftPreviewSurface(SampleData.streamedPartial, isRunning = true, darkTheme = true)

@Preview(name = "Draft · complete", showBackground = true)
@Composable
private fun DraftCompletePreview() = DraftPreviewSurface(SampleData.streamedComplete, isRunning = false)

@Preview(name = "Draft · complete · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DraftCompleteDarkPreview() = DraftPreviewSurface(SampleData.streamedComplete, isRunning = false, darkTheme = true)
