package com.llmlocal.recipe.config

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Per-app Koin module for the recipe app's LLM-task config + labels.
 *
 * The shared `:feature:llm-task` module's `viewModel { … }` definition
 * accepts these as Koin parameters at the call site, but we also expose
 * them here so other consumers (preview code, tests, future widgets) can
 * resolve `RecipeTaskConfig` / `RecipeLabels` directly.
 */
val recipesConfigModule: Module = module {
    single { RecipeTaskConfig }
    single { RecipeLabels }
}