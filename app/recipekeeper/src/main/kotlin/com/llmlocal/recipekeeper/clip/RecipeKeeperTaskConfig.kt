package com.llmlocal.recipekeeper.clip

import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.recipekeeper.domain.ClippedRecipe
import com.llmlocal.recipekeeper.parser.RecipeKeeperParser
import com.llmlocal.recipekeeper.prompt.RecipeKeeperPromptBuilder

/**
 * Prompt builder + parser pair for recipe extraction; `WebClipConfig.llmConfig`
 * and the thing `GenerateLlmTaskUseCase` runs.
 */
val RecipeKeeperTaskConfig: LlmTaskConfig<ClippedRecipe> = LlmTaskConfig(
    promptBuilder = RecipeKeeperPromptBuilder(),
    parser = RecipeKeeperParser(),
)
