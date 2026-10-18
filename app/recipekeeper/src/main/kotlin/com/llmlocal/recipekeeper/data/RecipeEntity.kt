package com.llmlocal.recipekeeper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for a clipped recipe. WS0 keeps it trivial — it exists to prove
 * the Room/KSP toolchain compiles; WS5a grows it to the full
 * `ClippedRecipe` shape (plus a `pictures` table) and bumps the schema.
 */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val clippedAt: Long,
)
