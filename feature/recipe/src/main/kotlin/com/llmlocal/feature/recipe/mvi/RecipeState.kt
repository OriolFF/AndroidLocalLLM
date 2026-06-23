package com.llmlocal.feature.recipe.mvi

import androidx.compose.runtime.Immutable
import com.llmlocal.core.model.Ingredient
import com.llmlocal.core.model.LlmModelDescriptor
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
    /** Currently selected model — null until the user picks one. */
    val selectedModel: LlmModelDescriptor? = null,
    /** True when the user has selected a model but the file is missing on disk. */
    val selectedModelInstalled: Boolean = false,
) {
    /**
     * Whether the Generate button is enabled. The recipe can only be
     * generated when there are ingredients AND no generation is in
     * flight AND a model is installed and the engine is ready.
     */
    val canGenerate: Boolean
        get() = ingredients.isNotEmpty() &&
            !isGenerating &&
            modelStatus is ModelStatus.Ready
}