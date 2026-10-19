package com.llmlocal.recipekeeper.clip.domain

import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperParser
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperPromptBuilder

/**
 * Prompt builder + parser pair for recipe extraction; `WebClipConfig.llmConfig`
 * and the thing `GenerateLlmTaskUseCase` runs.
 */
val RecipeKeeperTaskConfig: LlmTaskConfig<ClippedRecipe> = LlmTaskConfig(
    promptBuilder = RecipeKeeperPromptBuilder(),
    parser = RecipeKeeperParser(),
)
