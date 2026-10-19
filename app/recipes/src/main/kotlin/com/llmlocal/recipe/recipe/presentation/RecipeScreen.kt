package com.llmlocal.recipe.recipe.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.llmlocal.feature.llmtask.presentation.LlmTaskRoute
import com.llmlocal.recipe.config.rememberRecipeLabels
import com.llmlocal.recipe.config.RecipeTaskConfig
import com.llmlocal.recipe.recipe.domain.Recipe
import com.llmlocal.recipe.recipe.presentation.StreamedRecipeCard

/**
 * The recipe app's wrapper around the generic
 * [com.llmlocal.feature.llmtask.presentation.LlmTaskRoute].
 *
 * Provides:
 *  - the recipe-themed labels from [rememberRecipeLabels]
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
    val labels = rememberRecipeLabels()
    LlmTaskRoute(
        config = RecipeTaskConfig,
        labels = labels,
        renderOutput = { state ->
            @Suppress("UNCHECKED_CAST")
            val recipe = state.result as? Recipe
            StreamedRecipeCard(
                streamedText = state.streamedText,
                recipe = recipe,
                isGenerating = state.isGenerating,
                warmingUpLabel = labels.warmingUpLabel,
            )
        },
        modifier = modifier,
        onNavigateToModelManagement = onNavigateToModelManagement,
    )
}