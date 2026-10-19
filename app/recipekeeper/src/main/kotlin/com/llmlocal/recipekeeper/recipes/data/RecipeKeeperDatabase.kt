package com.llmlocal.recipekeeper.recipes.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Recipe Keeper's Room database: `recipes` + `pictures`. Schemas are
 * exported to `app/recipekeeper/schemas/` by the `llmlocal.android.room`
 * convention plugin. Version 1 shipped (including WS11's `picturesFailed` /
 * `pictureCandidates`), so every schema change from now on needs a
 * migration and a committed `N.json`; never `fallbackToDestructiveMigration`.
 * Version 2 adds `isFavourite`, `editedAt` and `origin` ([MIGRATION_1_2]).
 */
@Database(
    entities = [RecipeEntity::class, PictureEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(RecipeKeeperConverters::class)
abstract class RecipeKeeperDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    companion object {
        const val NAME = "recipekeeper.db"
    }
}

/**
 * v1 → v2: favourites, edit timestamp and recipe origin. The column
 * definitions (types, `NOT NULL`, defaults) must match the `@ColumnInfo`
 * defaults on [RecipeEntity] exactly or Room's identity check fails.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN isFavourite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE recipes ADD COLUMN editedAt INTEGER")
        db.execSQL("ALTER TABLE recipes ADD COLUMN origin TEXT NOT NULL DEFAULT 'CLIPPED'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipes_isFavourite ON recipes (isFavourite)")
    }
}
