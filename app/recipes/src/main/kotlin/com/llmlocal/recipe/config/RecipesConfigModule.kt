package com.llmlocal.recipe.config

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Per-app Koin module for the recipe app's LLM-task config.
 *
 * The shared `:feature:llm-task` module's `viewModel { … }` definition
 * accepts config + labels as Koin parameters at the call site (labels are
 * built from resources via `rememberRecipeLabels()`), but we also expose the config
 * here so other consumers (preview code, tests, future widgets) can
 * resolve `RecipeTaskConfig` directly.
 */
val recipesConfigModule: Module = module {
    single { RecipeTaskConfig }
}