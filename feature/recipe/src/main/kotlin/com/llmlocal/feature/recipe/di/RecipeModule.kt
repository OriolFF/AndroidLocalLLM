package com.llmlocal.feature.recipe.di

import com.llmlocal.core.llm.di.REAL_ENGINE_QUALIFIER
import com.llmlocal.feature.recipe.RecipeViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module wiring for the recipe feature.
 *
 * The view model only depends on the real engine — the demo engine has
 * been removed. Model selection lives in
 * [com.llmlocal.core.llm.selection.LlmModelSelectionStore].
 */
val recipeModule: Module = module {
    viewModel {
        RecipeViewModel(
            generateRecipe = get(),
            modelManager = get(),
            selectionStore = get(),
            realEngine = get(named(REAL_ENGINE_QUALIFIER)),
            dispatchers = get(),
        )
    }
}