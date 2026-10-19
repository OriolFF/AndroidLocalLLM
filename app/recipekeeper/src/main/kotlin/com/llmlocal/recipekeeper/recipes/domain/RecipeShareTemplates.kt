package com.llmlocal.recipekeeper.recipes.domain

/**
 * Share text per [RecipeOrigin]; built from the labels in DI so the domain
 * holds no copy. Clipped recipes share title + link, created recipes share
 * their full text.
 */
data class RecipeShareTemplates(
    val clipped: (title: String, url: String) -> String,
    val created: (title: String, ingredientsHeader: String, ingredientLines: String, stepsHeader: String, stepLines: String) -> String,
    val bullet: (String) -> String,
    val numbered: (Int, String) -> String,
    val ingredientsHeader: String,
    val stepsHeader: String,
)
