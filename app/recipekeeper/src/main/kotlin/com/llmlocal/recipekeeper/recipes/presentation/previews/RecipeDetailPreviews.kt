package com.llmlocal.recipekeeper.recipes.presentation.previews

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.recipekeeper.recipes.presentation.rememberRecipeKeeperLibraryLabels
import com.llmlocal.recipekeeper.designsystem.RecipeKeeperTokens
import com.llmlocal.recipekeeper.recipes.presentation.detail.RecipeDetailScreen
import com.llmlocal.recipekeeper.recipes.presentation.detail.RecipeDetailState

/**
 * `@Preview`s for the recipe detail screen: gradient hero (no picture),
 * photo hero (the sample paths don't resolve, so Coil shows the
 * placeholder surface — layout and scrims still render), truncated,
 * delete dialog, loading, not found, dark, and the 600dp+ two-pane split.
 */
@Composable
private fun DetailPreviewSurface(state: RecipeDetailState, darkTheme: Boolean = false) {
    AppTheme(tokens = RecipeKeeperTokens, darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            RecipeDetailScreen(
                state = state,
                labels = rememberRecipeKeeperLibraryLabels(),
                snackbarHostState = remember { SnackbarHostState() },
                onIntent = {},
                onBack = {},
            )
        }
    }
}

private val loaded = RecipeDetailState(recipe = SampleData.recipe, isLoading = false, currentStep = 1)

@Preview(name = "Detail · gradient hero", showBackground = true)
@Composable
private fun DetailGradientPreview() = DetailPreviewSurface(loaded)

@Preview(name = "Detail · gradient hero · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetailGradientDarkPreview() = DetailPreviewSurface(loaded, darkTheme = true)

@Preview(name = "Detail · photo hero + pager", showBackground = true)
@Composable
private fun DetailPhotoPreview() = DetailPreviewSurface(
    loaded.copy(recipe = SampleData.recipeWithPictures),
)

@Preview(name = "Detail · favourite + edited", showBackground = true)
@Composable
private fun DetailEditedPreview() = DetailPreviewSurface(
    loaded.copy(recipe = SampleData.editedRecipe, currentStep = null),
)

@Preview(name = "Detail · created", showBackground = true)
@Composable
private fun DetailCreatedPreview() = DetailPreviewSurface(
    loaded.copy(recipe = SampleData.createdRecipe, currentStep = null),
)

@Preview(name = "Detail · created · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetailCreatedDarkPreview() = DetailPreviewSurface(
    loaded.copy(recipe = SampleData.createdRecipe, currentStep = null),
    darkTheme = true,
)

@Preview(name = "Detail · from images", showBackground = true)
@Composable
private fun DetailImagesPreview() = DetailPreviewSurface(
    loaded.copy(recipe = SampleData.imagesRecipe, currentStep = null),
)

@Preview(name = "Detail · truncated", showBackground = true)
@Composable
private fun DetailTruncatedPreview() = DetailPreviewSurface(
    loaded.copy(recipe = SampleData.truncatedRecipe, currentStep = null),
)

@Preview(name = "Detail · pictures unavailable", showBackground = true)
@Composable
private fun DetailPicturesUnavailablePreview() = DetailPreviewSurface(
    loaded.copy(recipe = SampleData.picturesFailedRecipe, currentStep = null),
)

@Preview(name = "Detail · retrying pictures · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetailRetryingPicturesDarkPreview() = DetailPreviewSurface(
    loaded.copy(recipe = SampleData.picturesFailedRecipe, currentStep = null, retryingPictures = true),
    darkTheme = true,
)

@Preview(name = "Detail · delete dialog", showBackground = true)
@Composable
private fun DetailDeleteDialogPreview() = DetailPreviewSurface(loaded.copy(showDeleteDialog = true))

@Preview(name = "Detail · loading", showBackground = true)
@Composable
private fun DetailLoadingPreview() = DetailPreviewSurface(RecipeDetailState())

@Preview(name = "Detail · not found · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetailNotFoundDarkPreview() = DetailPreviewSurface(
    RecipeDetailState(isLoading = false),
    darkTheme = true,
)

@Preview(name = "Detail · two-pane 840dp", showBackground = true, widthDp = 840, heightDp = 600)
@Composable
private fun DetailTwoPanePreview() = DetailPreviewSurface(loaded)

@Preview(name = "Detail · two-pane · photo · dark", showBackground = true, widthDp = 840, heightDp = 600, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetailTwoPaneDarkPreview() = DetailPreviewSurface(
    loaded.copy(recipe = SampleData.recipeWithPictures),
    darkTheme = true,
)

@Preview(name = "Detail · landscape phone", showBackground = true, widthDp = 800, heightDp = 360)
@Composable
private fun DetailLandscapePreview() = DetailPreviewSurface(loaded)
