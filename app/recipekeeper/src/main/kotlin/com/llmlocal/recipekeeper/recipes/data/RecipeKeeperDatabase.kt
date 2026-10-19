package com.llmlocal.recipekeeper.recipes.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Recipe Keeper's Room database: `recipes` + `pictures`. Schemas are
 * exported to `app/recipekeeper/schemas/` by the `llmlocal.android.room`
 * convention plugin; version 1 is the first shipped schema (WS0's trivial
 * table was never released, so it was replaced in place rather than
 * migrated). WS11 added `picturesFailed` / `pictureCandidates` to
 * `recipes` **still as version 1** — nothing has shipped, so `1.json` was
 * regenerated instead of adding a migration. QA installs that hold a
 * pre-WS11 database must be cleared (`pm clear` / reinstall), otherwise
 * Room refuses to open it (identity hash mismatch).
 */
@Database(
    entities = [RecipeEntity::class, PictureEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RecipeKeeperConverters::class)
abstract class RecipeKeeperDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    companion object {
        const val NAME = "recipekeeper.db"
    }
}
