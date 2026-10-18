package com.llmlocal.recipekeeper.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Recipe Keeper's Room database. Schemas are exported to
 * `app/recipekeeper/schemas/` by the `llmlocal.android.room` convention
 * plugin. Built and registered in Koin by WS5a.
 */
@Database(entities = [RecipeEntity::class], version = 1)
abstract class RecipeKeeperDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
}
