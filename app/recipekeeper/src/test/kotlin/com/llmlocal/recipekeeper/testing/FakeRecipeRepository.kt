package com.llmlocal.recipekeeper.testing

import com.llmlocal.recipekeeper.domain.ClippedRecipe
import com.llmlocal.recipekeeper.domain.RecipeRepository
import com.llmlocal.recipekeeper.domain.RecipeSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [RecipeRepository]: a `StateFlow` of rows, newest first,
 * `LIKE`-style search over title and ingredients. Records every
 * `setChecked` / `delete` call for assertions.
 */
class FakeRecipeRepository(initial: List<ClippedRecipe> = emptyList()) : RecipeRepository {

    private val rows = MutableStateFlow(initial.associateBy { it.id })

    val checkedCalls = mutableListOf<Pair<String, Set<Int>>>()
    val deleteCalls = mutableListOf<String>()

    val current: List<ClippedRecipe>
        get() = rows.value.values.sortedByDescending { it.clippedAt }

    /** Every stored recipe, including soft-deleted ones (used by the pipeline tests). */
    val recipes: List<ClippedRecipe>
        get() = rows.value.values.toList()

    override fun observeAll(): Flow<List<RecipeSummary>> =
        rows.map { map -> map.values.sortedByDescending { it.clippedAt }.map { it.toSummary() } }

    override fun observe(id: String): Flow<ClippedRecipe?> = rows.map { it[id] }

    override suspend fun upsert(recipe: ClippedRecipe) {
        rows.update { it + (recipe.id to recipe) }
    }

    override suspend fun delete(id: String) {
        deleteCalls += id
        softDeleted -= id
        rows.update { it - id }
    }

    /** Ids hidden by [softDelete] but still present until [delete]/[purgeDeleted]. */
    val softDeleted = mutableSetOf<String>()

    override suspend fun softDelete(id: String) {
        if (id in rows.value) softDeleted += id
    }

    override suspend fun restore(id: String) {
        softDeleted -= id
    }

    override suspend fun purgeDeleted(olderThanMillis: Long): List<String> {
        val ids = softDeleted.toList()
        ids.forEach { delete(it) }
        return ids
    }

    override suspend fun setChecked(id: String, checked: Set<Int>) {
        checkedCalls += id to checked
        rows.update { map ->
            val recipe = map[id] ?: return@update map
            map + (id to recipe.copy(checkedIngredients = checked))
        }
    }

    val replacePicturesCalls = mutableListOf<Triple<String, List<String>, Boolean>>()

    override suspend fun replacePictures(id: String, pictures: List<String>, picturesFailed: Boolean) {
        replacePicturesCalls += Triple(id, pictures, picturesFailed)
        rows.update { map ->
            val recipe = map[id] ?: return@update map
            map + (id to recipe.copy(pictures = pictures, picturesFailed = picturesFailed))
        }
    }

    override fun search(query: String): Flow<List<RecipeSummary>> = rows.map { map ->
        val q = query.trim().lowercase()
        map.values
            .filter { r -> r.title.lowercase().contains(q) || r.ingredients.any { it.lowercase().contains(q) } }
            .sortedByDescending { it.clippedAt }
            .map { it.toSummary() }
    }

    override suspend fun findIdBySourceUrl(url: String): String? =
        rows.value.values.filter { it.sourceUrl == url && it.id !in softDeleted }.maxByOrNull { it.clippedAt }?.id

    private fun ClippedRecipe.toSummary() = RecipeSummary(
        id = id,
        title = title,
        sourceHost = sourceHost,
        totalMinutes = totalMinutes,
        heroPicture = pictures.firstOrNull(),
        truncated = truncated,
        clippedAt = clippedAt,
    )

    companion object {
        fun recipe(
            id: String,
            title: String,
            clippedAt: Long,
            ingredients: List<String> = listOf("flour", "eggs"),
            checked: Set<Int> = emptySet(),
            picturesFailed: Boolean = false,
            pictureCandidates: List<String> = emptyList(),
        ) = ClippedRecipe(
            id = id,
            title = title,
            description = null,
            servings = "4",
            prepMinutes = 10,
            cookMinutes = 20,
            totalMinutes = 30,
            ingredients = ingredients,
            steps = listOf("Mix.", "Bake."),
            notes = null,
            sourceUrl = "https://example.com/$id",
            sourceHost = "example.com",
            pictures = emptyList(),
            truncated = false,
            clippedAt = clippedAt,
            checkedIngredients = checked,
            picturesFailed = picturesFailed,
            pictureCandidates = pictureCandidates,
        )
    }
}
