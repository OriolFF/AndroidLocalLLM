package com.llmlocal.recipekeeper.recipes.domain

/**
 * What the library grid needs per recipe — a projection of [ClippedRecipe]
 * without ingredients / steps so lists stay cheap.
 *
 * @property heroPicture local path of the first picture, or `null`
 */
data class RecipeSummary(
    val id: String,
    val title: String,
    val sourceHost: String,
    val totalMinutes: Int?,
    val heroPicture: String?,
    val truncated: Boolean,
    val clippedAt: Long,
)
