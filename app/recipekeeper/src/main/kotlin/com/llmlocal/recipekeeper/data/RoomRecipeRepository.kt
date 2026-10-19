package com.llmlocal.recipekeeper.data

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.domain.ClippedRecipe
import com.llmlocal.recipekeeper.domain.RecipeRepository
import com.llmlocal.recipekeeper.domain.RecipeSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [RecipeRepository] over Room. Hard deletes also remove the picture folder
 * through [RecipePictureStore]; soft delete / restore only flip
 * `deletedAt`.
 */
class RoomRecipeRepository(
    private val dao: RecipeDao,
    private val pictures: RecipePictureStore,
    private val dispatchers: DispatcherProvider,
    private val clock: () -> Long = System::currentTimeMillis,
) : RecipeRepository {

    override fun observeAll(): Flow<List<RecipeSummary>> =
        dao.observeSummaries().map { rows -> rows.map(RecipeSummaryRow::toDomain) }

    override fun observe(id: String): Flow<ClippedRecipe?> =
        dao.observeWithPictures(id).map { it?.toDomain() }

    override suspend fun upsert(recipe: ClippedRecipe) {
        // Keep an existing soft-delete flag; re-saving a recipe mid-undo is
        // an edge case but must not resurrect it silently.
        val deletedAt = dao.deletedAtOf(recipe.id)
        dao.upsertWithPictures(recipe.toEntity(deletedAt = deletedAt), recipe.toPictureEntities())
    }

    override suspend fun softDelete(id: String) {
        dao.setDeletedAt(id, clock())
    }

    override suspend fun restore(id: String) {
        dao.setDeletedAt(id, null)
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
        withContext(dispatchers.io) { pictures.deleteAll(id) }
    }

    override suspend fun purgeDeleted(olderThanMillis: Long): List<String> {
        val ids = dao.deletedBefore(olderThanMillis)
        ids.forEach { delete(it) }
        return ids
    }

    override suspend fun setChecked(id: String, checked: Set<Int>) {
        dao.setChecked(id, checked)
    }

    override suspend fun replacePictures(id: String, pictures: List<String>, picturesFailed: Boolean) {
        dao.replacePictures(
            id = id,
            pictures = pictures.mapIndexed { index, path -> PictureEntity(recipeId = id, position = index, path = path) },
            picturesFailed = picturesFailed,
        )
    }

    override fun search(query: String): Flow<List<RecipeSummary>> {
        val q = query.trim()
        if (q.isEmpty()) return observeAll()
        return dao.searchSummaries(q).map { rows -> rows.map(RecipeSummaryRow::toDomain) }
    }
}
