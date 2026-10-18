package com.llmlocal.recipe

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.llmlocal.feature.llmtask.LlmTaskRoute
import com.llmlocal.recipe.config.RecipeLabels
import com.llmlocal.recipe.config.RecipeTaskConfig
import com.llmlocal.recipe.domain.Recipe
import com.llmlocal.recipe.output.StreamedRecipeCard

/**
 * The recipe app's wrapper around the generic
 * [com.llmlocal.feature.llmtask.LlmTaskRoute].
 *
 * Provides:
 *  - the recipe-themed [RecipeLabels]
 *  - the [RecipeTaskConfig] that binds prompt + parser
 *  - a [renderOutput] lambda that casts `state.result` to `Recipe` and
 *    renders the [StreamedRecipeCard] with streamed text + parsed recipe
 *
 * The `LlmTaskRoute` host owns the `LlmTaskViewModel` via Koin's parameter
 * mechanism — call sites only pass the config + labels + output renderer.
 */
@Composable
fun RecipeScreen(
    modifier: Modifier = Modifier,
    onNavigateToModelManagement: () -> Unit = {},
) {
    LlmTaskRoute(
        config = RecipeTaskConfig,
        labels = RecipeLabels,
        renderOutput = { state ->
            @Suppress("UNCHECKED_CAST")
            val recipe = state.result as? Recipe
            StreamedRecipeCard(
                streamedText = state.streamedText,
                recipe = recipe,
                isGenerating = state.isGenerating,
                warmingUpLabel = RecipeLabels.warmingUpLabel,
            )
        },
        modifier = modifier,
        onNavigateToModelManagement = onNavigateToModelManagement,
    )
}