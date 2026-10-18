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

    override fun observeAll(): Flow<List<RecipeSummary>> =
        rows.map { map -> map.values.sortedByDescending { it.clippedAt }.map { it.toSummary() } }

    override fun observe(id: String): Flow<ClippedRecipe?> = rows.map { it[id] }

    override suspend fun upsert(recipe: ClippedRecipe) {
        rows.update { it + (recipe.id to recipe) }
    }

    override suspend fun delete(id: String) {
        deleteCalls += id
        rows.update { it - id }
    }

    override suspend fun setChecked(id: String, checked: Set<Int>) {
        checkedCalls += id to checked
        rows.update { map ->
            val recipe = map[id] ?: return@update map
            map + (id to recipe.copy(checkedIngredients = checked))
        }
    }

    override fun search(query: String): Flow<List<RecipeSummary>> = rows.map { map ->
        val q = query.trim().lowercase()
        map.values
            .filter { r -> r.title.lowercase().contains(q) || r.ingredients.any { it.lowercase().contains(q) } }
            .sortedByDescending { it.clippedAt }
            .map { it.toSummary() }
    }

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
        )
    }
}
