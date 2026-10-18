package com.llmlocal.recipekeeper.domain

/**
 * A recipe clipped from a web page and saved in the library.
 *
 * Produced by `RecipeKeeperParser` (model output) or
 * `SchemaOrgRecipeMapper` (deterministic fallback), persisted by
 * `RecipeRepository`.
 *
 * @property servings free text as the source wrote it ("4", "6–8 cookies")
 * @property pictures local file paths under `filesDir/recipes/<id>/`;
 *   the first one is the hero
 * @property truncated the model output looked cut off; detail shows
 *   "Possibly incomplete"
 * @property clippedAt epoch millis
 * @property checkedIngredients indices into [ingredients] the user ticked off
 */
data class ClippedRecipe(
    val id: String,
    val title: String,
    val description: String?,
    val servings: String?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val totalMinutes: Int?,
    val ingredients: List<String>,
    val steps: List<String>,
    val notes: String?,
    val sourceUrl: String,
    val sourceHost: String,
    val pictures: List<String>,
    val truncated: Boolean,
    val clippedAt: Long,
    val checkedIngredients: Set<Int> = emptySet(),
)
