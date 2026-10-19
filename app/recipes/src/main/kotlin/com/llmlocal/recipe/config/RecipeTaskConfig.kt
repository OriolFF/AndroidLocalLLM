package com.llmlocal.recipe.config

import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.recipe.recipe.domain.Recipe
import com.llmlocal.recipe.recipe.domain.RecipeParser
import com.llmlocal.recipe.recipe.domain.RecipePromptBuilder

/**
 * The recipe app's [LlmTaskConfig] — binds the prompt builder and parser
 * for recipe generation. Provided to Koin via [recipesConfigModule].
 */
val RecipeTaskConfig: LlmTaskConfig<Recipe> = LlmTaskConfig(
    promptBuilder = RecipePromptBuilder(),
    parser = RecipeParser(),
)