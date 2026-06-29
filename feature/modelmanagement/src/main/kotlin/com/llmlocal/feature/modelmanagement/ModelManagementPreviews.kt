package com.llmlocal.feature.modelmanagement

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.feature.modelmanagement.mvi.ModelFilters
import com.llmlocal.feature.modelmanagement.mvi.ModelManagementIntent
import com.llmlocal.feature.modelmanagement.mvi.ModelManagementState

/**
 * @Preview composables for the model-management screen.
 * Each preview renders the screen in a single [AppTheme] so designers
 * can iterate on the visual treatment without rebuilding the app.
 *
 *  - `EmptyCatalogPreview`        — no models match (filter set is restrictive).
 *  - `DownloadingPreview`         — one model mid-download.
 *  - `SelectedAndInstalledPreview`— one model active + one installed, others available.
 *  - `DownloadedFailedPreview`    — one model failed.
 *  - `DarkPreview`                — same as `SelectedAndInstalledPreview`, dark.
 */

@Composable
private fun PreviewSurface(
    darkTheme: Boolean = false,
    state: ModelManagementState,
) {
    AppTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ModelManagementScreen(
                state = state,
                onIntent = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val emptyCatalog = ModelManagementState(
    catalog = LlmModelCatalog.ALL,
    installedIds = emptySet(),
    selectedId = null,
    downloading = emptyMap(),
    filters = ModelFilters.DEFAULT,
    filteredCatalog = emptyList(),
)

private val fullCatalogState = ModelManagementState(
    catalog = LlmModelCatalog.ALL,
    installedIds = setOf(LlmModelCatalog.DEFAULT_MODEL.id),
    selectedId = LlmModelCatalog.DEFAULT_MODEL.id,
    downloading = emptyMap(),
    filters = ModelFilters.DEFAULT,
    filteredCatalog = LlmModelCatalog.ALL,
)

private val downloadingState = fullCatalogState.copy(
    downloading = mapOf(
        LlmModelCatalog.ALL[1].id to PerModelProgress(
            modelId = LlmModelCatalog.ALL[1].id,
            state = DownloadState.RUNNING,
            bytesRead = 420L * 1024 * 1024,
            totalBytes = 980L * 1024 * 1024,
            speedBytesPerSec = 4L * 1024 * 1024,
            etaSeconds = 142,
            updatedAt = 0L,
        ),
    ),
)

private val failedState = fullCatalogState.copy(
    downloading = mapOf(
        LlmModelCatalog.ALL[2].id to PerModelProgress(
            modelId = LlmModelCatalog.ALL[2].id,
            state = DownloadState.FAILED,
            bytesRead = 100L * 1024 * 1024,
            totalBytes = 2_300L * 1024 * 1024,
            speedBytesPerSec = 0L,
            etaSeconds = null,
            updatedAt = 0L,
            failureReason = "Network error — check your connection and try again.",
        ),
    ),
)

@Preview(name = "Models / Available (light)", showBackground = true, heightDp = 1100)
@Composable
private fun AvailableLightPreview() {
    PreviewSurface(state = fullCatalogState)
}

@Preview(name = "Models / Downloading", showBackground = true, heightDp = 1100)
@Composable
private fun DownloadingPreview() {
    PreviewSurface(state = downloadingState)
}

@Preview(name = "Models / Failed", showBackground = true, heightDp = 1100)
@Composable
private fun FailedPreview() {
    PreviewSurface(state = failedState)
}

@Preview(name = "Models / No matches", showBackground = true, heightDp = 800)
@Composable
private fun NoMatchesPreview() {
    PreviewSurface(
        state = fullCatalogState.copy(filteredCatalog = emptyList()),
    )
}

@Preview(name = "Models / Dark", showBackground = true, heightDp = 1100)
@Composable
private fun DarkPreview() {
    PreviewSurface(darkTheme = true, state = fullCatalogState)
}
