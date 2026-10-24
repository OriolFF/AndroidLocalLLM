package com.llmlocal.recipekeeper.recipes.presentation.previews

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.llmlocal.core.designsystem.component.AppScaffold
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.recipekeeper.recipes.presentation.rememberRecipeKeeperLibraryLabels
import com.llmlocal.recipekeeper.designsystem.RecipeKeeperTokens
import com.llmlocal.recipekeeper.recipes.presentation.library.LibraryFab
import com.llmlocal.recipekeeper.recipes.domain.LibraryFilter
import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatus
import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability
import com.llmlocal.recipekeeper.recipes.presentation.library.ImagesUnavailableDialog
import com.llmlocal.recipekeeper.recipes.presentation.library.LibraryScreen
import com.llmlocal.recipekeeper.recipes.presentation.library.SourceChooserContent
import com.llmlocal.recipekeeper.recipes.presentation.library.LibraryState

/**
 * `@Preview`s for the library screen in every state, light and dark, plus a
 * 600dp-wide variant for the adaptive grid. Rendered inside the same
 * `AppScaffold` + FAB slot the route uses.
 */
@Composable
private fun LibraryPreviewSurface(state: LibraryState, darkTheme: Boolean = false) {
    val labels = rememberRecipeKeeperLibraryLabels()
    AppTheme(tokens = RecipeKeeperTokens, darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppScaffold(
                title = labels.libraryTitle,
                floatingActionButton = { LibraryFab(state = state, labels = labels, onIntent = {}) },
            ) { padding ->
                LibraryScreen(
                    state = state,
                    labels = labels,
                    onIntent = {},
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

private val gridReady = LibraryState(
    recipes = SampleData.summaries,
    isLoading = false,
    modelStatus = LibraryModelStatus.Ready(),
)

private val gridWithImages = gridReady.copy(recipes = listOf(SampleData.imagesSummary) + SampleData.summaries)

@Preview(name = "Library · images tile", showBackground = true)
@Composable
private fun LibraryImagesTilePreview() = LibraryPreviewSurface(gridWithImages)

@Composable
private fun SourceChooserPreviewSurface(availability: ImagesAvailability, darkTheme: Boolean = false) {
    val labels = rememberRecipeKeeperLibraryLabels()
    AppTheme(tokens = RecipeKeeperTokens, darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            SourceChooserContent(imagesAvailability = availability, labels = labels, onIntent = {})
        }
    }
}

@Preview(name = "Chooser · images available", showBackground = true)
@Composable
private fun ChooserAvailablePreview() = SourceChooserPreviewSurface(ImagesAvailability.Available)

@Preview(name = "Chooser · needs a model", showBackground = true)
@Composable
private fun ChooserNoModelPreview() = SourceChooserPreviewSurface(ImagesAvailability.NoModel)

@Preview(name = "Chooser · model can't read images", showBackground = true)
@Composable
private fun ChooserLacksVisionPreview() = SourceChooserPreviewSurface(ImagesAvailability.ModelLacksVision)

@Preview(name = "Chooser · images hidden", showBackground = true)
@Composable
private fun ChooserDisabledPreview() = SourceChooserPreviewSurface(ImagesAvailability.Disabled)

@Preview(name = "Chooser · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChooserDarkPreview() = SourceChooserPreviewSurface(ImagesAvailability.Available, darkTheme = true)

@Preview(name = "Images unavailable dialog", showBackground = true)
@Composable
private fun ImagesUnavailableDialogPreview() {
    val labels = rememberRecipeKeeperLibraryLabels()
    AppTheme(tokens = RecipeKeeperTokens) {
        ImagesUnavailableDialog(modelName = "Phi-3.5 Mini", labels = labels, onOpenModels = {}, onDismiss = {})
    }
}

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
    LibraryState(isLoading = false, modelStatus = LibraryModelStatus.Ready()),
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
    LibraryState(query = "ramen", searchActive = true, isLoading = false, modelStatus = LibraryModelStatus.Ready()),
)

@Preview(name = "Library · searching", showBackground = true)
@Composable
private fun LibrarySearchingPreview() = LibraryPreviewSurface(
    gridReady.copy(query = "soup", searchActive = true, recipes = SampleData.summaries.take(1)),
)

@Preview(name = "Library · favourites on", showBackground = true)
@Composable
private fun LibraryFavouritesPreview() = LibraryPreviewSurface(
    gridReady.copy(recipes = SampleData.favouriteSummaries, filter = LibraryFilter(favouritesOnly = true)),
)

@Preview(name = "Library · favourites on · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryFavouritesDarkPreview() = LibraryPreviewSurface(
    gridReady.copy(recipes = SampleData.favouriteSummaries, filter = LibraryFilter(favouritesOnly = true)),
    darkTheme = true,
)

@Preview(name = "Library · favourites empty", showBackground = true)
@Composable
private fun LibraryFavouritesEmptyPreview() = LibraryPreviewSurface(
    gridReady.copy(recipes = emptyList(), filter = LibraryFilter(favouritesOnly = true)),
)

@Preview(name = "Library · favourites + search empty · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryFavouritesSearchEmptyPreview() = LibraryPreviewSurface(
    gridReady.copy(recipes = emptyList(), query = "ramen", searchActive = true, filter = LibraryFilter(favouritesOnly = true)),
    darkTheme = true,
)

@Preview(name = "Library · loading", showBackground = true)
@Composable
private fun LibraryLoadingPreview() = LibraryPreviewSurface(LibraryState())

@Preview(name = "Library · 600dp", showBackground = true, widthDp = 640, heightDp = 480)
@Composable
private fun LibraryWidePreview() = LibraryPreviewSurface(gridReady)
