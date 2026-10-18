package com.llmlocal.recipekeeper.previews

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.recipekeeper.config.DefaultRecipeKeeperLibraryLabels
import com.llmlocal.recipekeeper.designsystem.RecipeKeeperTokens
import com.llmlocal.recipekeeper.library.LibraryModelStatus
import com.llmlocal.recipekeeper.library.LibraryScreen
import com.llmlocal.recipekeeper.library.LibraryState

/**
 * `@Preview`s for the library screen in every state, light and dark, plus a
 * 600dp-wide variant for the adaptive grid.
 */
@Composable
private fun LibraryPreviewSurface(state: LibraryState, darkTheme: Boolean = false) {
    AppTheme(tokens = RecipeKeeperTokens, darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibraryScreen(
                state = state,
                labels = DefaultRecipeKeeperLibraryLabels,
                onIntent = {},
            )
        }
    }
}

private val gridReady = LibraryState(
    recipes = SampleData.summaries,
    isLoading = false,
    modelStatus = LibraryModelStatus.Ready,
)

@Preview(name = "Library · grid", showBackground = true)
@Composable
private fun LibraryGridPreview() = LibraryPreviewSurface(gridReady)

@Preview(name = "Library · grid · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryGridDarkPreview() = LibraryPreviewSurface(gridReady, darkTheme = true)

@Preview(name = "Library · no model", showBackground = true)
@Composable
private fun LibraryNoModelPreview() = LibraryPreviewSurface(
    gridReady.copy(modelStatus = LibraryModelStatus.NoModel),
)

@Preview(name = "Library · no model · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryNoModelDarkPreview() = LibraryPreviewSurface(
    gridReady.copy(modelStatus = LibraryModelStatus.NoModel),
    darkTheme = true,
)

@Preview(name = "Library · downloading", showBackground = true)
@Composable
private fun LibraryDownloadingPreview() = LibraryPreviewSurface(
    gridReady.copy(modelStatus = LibraryModelStatus.Downloading(SampleData.downloading)),
)

@Preview(name = "Library · empty", showBackground = true)
@Composable
private fun LibraryEmptyPreview() = LibraryPreviewSurface(
    LibraryState(isLoading = false, modelStatus = LibraryModelStatus.Ready),
)

@Preview(name = "Library · empty · no model · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryEmptyNoModelDarkPreview() = LibraryPreviewSurface(
    LibraryState(isLoading = false, modelStatus = LibraryModelStatus.NoModel),
    darkTheme = true,
)

@Preview(name = "Library · search empty", showBackground = true)
@Composable
private fun LibrarySearchEmptyPreview() = LibraryPreviewSurface(
    LibraryState(query = "ramen", searchActive = true, isLoading = false, modelStatus = LibraryModelStatus.Ready),
)

@Preview(name = "Library · searching", showBackground = true)
@Composable
private fun LibrarySearchingPreview() = LibraryPreviewSurface(
    gridReady.copy(query = "soup", searchActive = true, recipes = SampleData.summaries.take(1)),
)

@Preview(name = "Library · loading", showBackground = true)
@Composable
private fun LibraryLoadingPreview() = LibraryPreviewSurface(LibraryState())

@Preview(name = "Library · 600dp", showBackground = true, widthDp = 640, heightDp = 480)
@Composable
private fun LibraryWidePreview() = LibraryPreviewSurface(gridReady)
