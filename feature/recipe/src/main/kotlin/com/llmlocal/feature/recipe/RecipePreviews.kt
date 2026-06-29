package com.llmlocal.feature.recipe

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.core.model.Ingredient
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.core.model.ModelFamily
import com.llmlocal.core.model.Recipe
import com.llmlocal.feature.recipe.mvi.ModelStatus
import com.llmlocal.feature.recipe.mvi.RecipeIntent
import com.llmlocal.feature.recipe.mvi.RecipeState

/**
 * @Preview composables for the recipe screen + the recipe components.
 * Each preview renders the screen in a single [AppTheme] so designers
 * can iterate on the visual treatment without rebuilding the app.
 *
 *  - `RecipeScreenEmptyPreview`      — no model, no ingredients, no recipe.
 *  - `RecipeScreenReadyPreview`      — model ready, a few ingredients, idle.
 *  - `RecipeScreenStreamingPreview`  — model ready, ingredients, mid-stream.
 *  - `RecipeScreenDonePreview`       — model ready, structured recipe shown.
 *  - `RecipeScreenNoModelPreview`    — explicit no-model banner + hero.
 *  - `RecipeScreenDarkPreview`       — same as Ready in dark theme.
 */

// ---------------------------------------------------------------------------
// Catalog (test data)
// ---------------------------------------------------------------------------

private val SAMPLE_MODEL = LlmModelDescriptor(
    id = "gemma-4-E2B-it",
    displayName = "Gemma 4 E2B IT",
    description = "Google's compact instruction-tuned model.",
    url = "https://example.invalid/gemma-4-E2B-it.litertlm",
    sizeBytes = 2_580L * 1024 * 1024,
    filename = "gemma-4-E2B-it.litertlm",
    tags = listOf("instruction-tuned", "google"),
    license = "Gemma Terms",
    author = "Google",
    minRamMb = 4_096,
    family = ModelFamily.INSTRUCT,
)

private val SAMPLE_INGREDIENTS = listOf(
    Ingredient("Tomatoes"),
    Ingredient("Garlic"),
    Ingredient("Olive oil"),
    Ingredient("Fresh basil"),
    Ingredient("Pasta"),
)

private val SAMPLE_RECIPE = Recipe(
    title = "Tomato & Basil Pasta",
    ingredients = listOf(
        "400 g ripe tomatoes, chopped",
        "3 cloves garlic, minced",
        "60 ml olive oil",
        "Fresh basil, torn",
        "350 g pasta",
    ),
    steps = listOf(
        "Bring a large pot of salted water to a boil.",
        "Warm the olive oil over medium heat; sauté the garlic until fragrant.",
        "Add the tomatoes; simmer until they break down into a sauce (~10 min).",
        "Meanwhile, cook the pasta to al dente per the package directions.",
        "Toss the pasta with the sauce and finish with fresh basil.",
    ),
    notes = "Reserve a little pasta water to loosen the sauce if needed.",
)

// ---------------------------------------------------------------------------
// State helpers
// ---------------------------------------------------------------------------

private fun emptyState() = RecipeState(
    modelStatus = ModelStatus.Ready,
    selectedModel = SAMPLE_MODEL,
    selectedModelInstalled = true,
)

private fun readyState() = RecipeState(
    modelStatus = ModelStatus.Ready,
    selectedModel = SAMPLE_MODEL,
    selectedModelInstalled = true,
    ingredients = SAMPLE_INGREDIENTS,
)

private fun streamingState() = readyState().copy(
    isGenerating = true,
    streamedText = "Tomato & Basil Pasta\n\nIngredients\n" +
        "• 400 g ripe tomatoes, chopped\n• 3 cloves garlic, minced\n" +
        "• 60 ml olive oil\n\nSteps\n" +
        "1. Bring a large pot of salted water to a boil.\n" +
        "2. Warm the olive oil over medium heat; sauté the garlic until fra",
)

private fun doneState() = readyState().copy(recipe = SAMPLE_RECIPE)

private fun noModelState() = RecipeState(
    modelStatus = ModelStatus.NoModelAvailable,
    selectedModel = null,
)

private fun noModelButSelectedState() = RecipeState(
    modelStatus = ModelStatus.NoModelAvailable,
    selectedModel = SAMPLE_MODEL,
    selectedModelInstalled = false,
)

private fun errorState() = readyState().copy(
    errorMessage = "The model failed to start. Try restarting the app or " +
        "picking a different model.",
)

// ---------------------------------------------------------------------------
// Reusable preview wrapper
// ---------------------------------------------------------------------------

@Composable
private fun PreviewSurface(
    darkTheme: Boolean = false,
    state: RecipeState,
) {
    AppTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            RecipeScreen(
                state = state,
                onIntent = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "Recipe / Empty", showBackground = true, heightDp = 900)
@Composable
private fun RecipeScreenEmptyPreview() {
    PreviewSurface(state = emptyState())
}

@Preview(name = "Recipe / Ready", showBackground = true, heightDp = 900)
@Composable
private fun RecipeScreenReadyPreview() {
    PreviewSurface(state = readyState())
}

@Preview(name = "Recipe / Streaming", showBackground = true, heightDp = 900)
@Composable
private fun RecipeScreenStreamingPreview() {
    PreviewSurface(state = streamingState())
}

@Preview(name = "Recipe / Done", showBackground = true, heightDp = 1100)
@Composable
private fun RecipeScreenDonePreview() {
    PreviewSurface(state = doneState())
}

@Preview(name = "Recipe / NoModel", showBackground = true, heightDp = 900)
@Composable
private fun RecipeScreenNoModelPreview() {
    PreviewSurface(state = noModelState())
}

@Preview(name = "Recipe / SelectedMissing", showBackground = true, heightDp = 900)
@Composable
private fun RecipeScreenSelectedMissingPreview() {
    PreviewSurface(state = noModelButSelectedState())
}

@Preview(name = "Recipe / Error", showBackground = true, heightDp = 900)
@Composable
private fun RecipeScreenErrorPreview() {
    PreviewSurface(state = errorState())
}

@Preview(name = "Recipe / Ready (Dark)", showBackground = true, heightDp = 900)
@Composable
private fun RecipeScreenReadyDarkPreview() {
    PreviewSurface(darkTheme = true, state = readyState())
}

@Preview(name = "Recipe / Done (Dark)", showBackground = true, heightDp = 1100)
@Composable
private fun RecipeScreenDoneDarkPreview() {
    PreviewSurface(darkTheme = true, state = doneState())
}