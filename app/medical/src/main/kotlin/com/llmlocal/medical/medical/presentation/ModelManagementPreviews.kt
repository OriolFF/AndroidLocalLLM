package com.llmlocal.medical.medical.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.feature.modelmanagement.domain.DownloadStatus
import com.llmlocal.feature.modelmanagement.domain.DownloadProgress
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.feature.modelmanagement.presentation.ModelManagementScreen
import com.llmlocal.feature.modelmanagement.domain.ModelFilters
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementState
import com.llmlocal.medical.config.MedicalModelManagementLabels
import com.llmlocal.medical.designsystem.MedicalTokens

/**
 * @Preview composables for the medical app's model-management screen.
 * Mirrors the recipe app's previews — only the tokens + labels differ.
 */

@Composable
private fun PreviewSurface(
    darkTheme: Boolean = false,
    state: ModelManagementState,
) {
    AppTheme(tokens = MedicalTokens, darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ModelManagementScreen(
                state = state,
                onIntent = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

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
        LlmModelCatalog.ALL[1].id to DownloadProgress(
            modelId = LlmModelCatalog.ALL[1].id,
            status = DownloadStatus.RUNNING,
            bytesRead = 420L * 1024 * 1024,
            totalBytes = 980L * 1024 * 1024,
            speedBytesPerSec = 4L * 1024 * 1024,
            etaSeconds = 142,
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