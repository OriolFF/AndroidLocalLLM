package com.llmlocal.recipekeeper.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [RecipeEntity] + [PictureEntity].
 *
 * List queries exclude soft-deleted rows (`deletedAt IS NOT NULL`);
 * [observeWithPictures] does not, so a detail screen keeps rendering during
 * the undo window. [upsertWithPictures] replaces the picture list
 * atomically.
 */
@Dao
interface RecipeDao {

    @Query(
        """
        SELECT r.id, r.title, r.sourceHost, r.totalMinutes, r.truncated, r.clippedAt,
               (SELECT p.path FROM pictures p WHERE p.recipeId = r.id ORDER BY p.position LIMIT 1) AS heroPicture
        FROM recipes r
        WHERE r.deletedAt IS NULL
        ORDER BY r.clippedAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<RecipeSummaryRow>>

    @Query(
        """
        SELECT r.id, r.title, r.sourceHost, r.totalMinutes, r.truncated, r.clippedAt,
               (SELECT p.path FROM pictures p WHERE p.recipeId = r.id ORDER BY p.position LIMIT 1) AS heroPicture
        FROM recipes r
        WHERE r.deletedAt IS NULL
          AND (r.title LIKE '%' || :query || '%' OR r.ingredients LIKE '%' || :query || '%')
        ORDER BY r.clippedAt DESC
        """,
    )
    fun searchSummaries(query: String): Flow<List<RecipeSummaryRow>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeWithPictures(id: String): Flow<RecipeWithPictures?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getWithPictures(id: String): RecipeWithPictures?

    @Upsert
    suspend fun upsert(entity: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPictures(pictures: List<PictureEntity>)

    @Query("DELETE FROM pictures WHERE recipeId = :recipeId")
    suspend fun deletePictures(recipeId: String)

    /** Upserts the row and replaces its pictures in one transaction. */
    @Transaction
    suspend fun upsertWithPictures(entity: RecipeEntity, pictures: List<PictureEntity>) {
        upsert(entity)
        deletePictures(entity.id)
        if (pictures.isNotEmpty()) insertPictures(pictures)
    }

    /** Hard delete; picture rows cascade. */
    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE recipes SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun setDeletedAt(id: String, deletedAt: Long?)

    @Query("SELECT id FROM recipes WHERE deletedAt IS NOT NULL AND deletedAt < :olderThan")
    suspend fun deletedBefore(olderThan: Long): List<String>

    @Query("UPDATE recipes SET checkedIngredients = :checked WHERE id = :id")
    suspend fun setChecked(id: String, checked: Set<Int>)

    @Query("UPDATE recipes SET picturesFailed = :picturesFailed WHERE id = :id")
    suspend fun setPicturesFailed(id: String, picturesFailed: Boolean)

    @Query("SELECT COUNT(*) FROM recipes WHERE id = :id")
    suspend fun exists(id: String): Int

    /** Replaces the picture rows of an existing recipe and its `picturesFailed` flag in one transaction. */
    @Transaction
    suspend fun replacePictures(id: String, pictures: List<PictureEntity>, picturesFailed: Boolean) {
        if (exists(id) == 0) return
        deletePictures(id)
        if (pictures.isNotEmpty()) insertPictures(pictures)
        setPicturesFailed(id, picturesFailed)
    }

    @Query("SELECT deletedAt FROM recipes WHERE id = :id")
    suspend fun deletedAtOf(id: String): Long?

    @Query("SELECT id FROM recipes WHERE sourceUrl = :url AND deletedAt IS NULL ORDER BY clippedAt DESC LIMIT 1")
    suspend fun findIdBySourceUrl(url: String): String?
}
