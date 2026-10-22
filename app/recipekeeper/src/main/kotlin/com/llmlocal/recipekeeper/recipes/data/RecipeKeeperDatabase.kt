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
 * Version 2 adds `isFavourite`, `editedAt` and `origin` ([MIGRATION_1_2]);
 * version 3 adds the `detection_hints` table ([MIGRATION_2_3]).
 */
@Database(
    entities = [RecipeEntity::class, PictureEntity::class, DetectionHintEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(RecipeKeeperConverters::class)
abstract class RecipeKeeperDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun detectionHintDao(): DetectionHintDao

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

/** v2 → v3: the `detection_hints` table (learned detection vocabulary / site rules). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `detection_hints` (`host` TEXT NOT NULL, `kind` TEXT NOT NULL, `token` TEXT NOT NULL, " +
                "`confidence` INTEGER NOT NULL, `successes` INTEGER NOT NULL, `failures` INTEGER NOT NULL, `lastSeenAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`host`, `kind`, `token`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detection_hints_host` ON `detection_hints` (`host`)")
    }
}
