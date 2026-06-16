package com.llmlocal.feature.recipe.mvi

import androidx.compose.runtime.Immutable
import com.llmlocal.core.model.Ingredient
import com.llmlocal.core.model.Recipe

/**
 * The single source of truth for the recipe screen.
 *
 * Marked [Immutable] so Compose can skip recomposition when the reference
 * is unchanged.
 */
@Immutable
data class RecipeState(
    val input: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val modelStatus: ModelStatus = ModelStatus.Unknown,
    val isGenerating: Boolean = false,
    val streamedText: String = "",
    val recipe: Recipe? = null,
    val errorMessage: String? = null,
    /** When true, use the canned FakeLlmEngine and skip the model download. */
    val useDemoEngine: Boolean = false,
    /** Human-readable size of the default model (e.g. "2.58 GB") — for the banner. */
    val modelHumanSize: String = "",
    /** Filename of the default model — for the banner. */
    val modelFilename: String = "",
) {
    val canGenerate: Boolean
        get() = ingredients.isNotEmpty() &&
            !isGenerating &&
            (useDemoEngine || modelStatus == ModelStatus.Ready)
}
