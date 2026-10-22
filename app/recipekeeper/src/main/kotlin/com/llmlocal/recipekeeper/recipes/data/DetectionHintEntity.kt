package com.llmlocal.recipekeeper.recipes.data

import androidx.room.Entity
import androidx.room.Index

/**
 * Room row of a learned detection hint. [host] is `""` for a generic hint:
 * SQLite treats NULLs in a composite primary key as distinct, which would
 * let the same generic hint be inserted twice, so the generic host is the
 * empty string here and `null` only in the domain model.
 */
@Entity(
    tableName = "detection_hints",
    primaryKeys = ["host", "kind", "token"],
    indices = [Index(value = ["host"])],
)
data class DetectionHintEntity(
    val host: String,
    val kind: String,
    val token: String,
    val confidence: Int,
    val successes: Int,
    val failures: Int,
    val lastSeenAt: Long,
)
