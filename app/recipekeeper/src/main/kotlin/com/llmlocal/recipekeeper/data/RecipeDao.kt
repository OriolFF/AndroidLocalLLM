package com.llmlocal.recipekeeper.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Data access for [RecipeEntity]. Minimal in WS0; WS5a adds search and detail queries. */
@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes ORDER BY clippedAt DESC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Upsert
    suspend fun upsert(entity: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: String)
}
