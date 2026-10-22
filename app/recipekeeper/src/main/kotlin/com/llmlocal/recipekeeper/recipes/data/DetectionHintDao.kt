package com.llmlocal.recipekeeper.recipes.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Data access for [DetectionHintEntity]; generic hints have `host = ''`. */
@Dao
interface DetectionHintDao {

    /** Generic hints with `confidence ≥ minConfidence`. */
    @Query("SELECT * FROM detection_hints WHERE host = '' AND confidence >= :minConfidence")
    suspend fun genericAtLeast(minConfidence: Int): List<DetectionHintEntity>

    @Query("SELECT * FROM detection_hints WHERE host = :host")
    suspend fun forHost(host: String): List<DetectionHintEntity>

    @Query("SELECT * FROM detection_hints WHERE host = :host AND kind = :kind AND token = :token")
    suspend fun find(host: String, kind: String, token: String): DetectionHintEntity?

    @Upsert
    suspend fun upsert(hints: List<DetectionHintEntity>)

    @Query("DELETE FROM detection_hints WHERE host = :host AND kind = :kind AND token = :token")
    suspend fun delete(host: String, kind: String, token: String)

    /** Keeps the newest [keep] rows of [kind] by `lastSeenAt`. */
    @Query(
        """
        DELETE FROM detection_hints WHERE kind = :kind AND rowid NOT IN
            (SELECT rowid FROM detection_hints WHERE kind = :kind ORDER BY lastSeenAt DESC LIMIT :keep)
        """,
    )
    suspend fun trimKind(kind: String, keep: Int)

    @Query("DELETE FROM detection_hints")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM detection_hints")
    fun observeCount(): Flow<Int>
}
