package com.llmlocal.recipekeeper.recipes.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1 → v2: two rows with pictures keep every value; the new columns take
 * their defaults. Run only this class on the QA emulator
 * (`-Pandroid.testInstrumentationRunnerArguments.class=…MigrationTest`).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RecipeKeeperDatabase::class.java,
    )

    @Test
    fun migrate1To2_keepsRowsAndFillsDefaults() {
        helper.createDatabase(DB, 1).use { db ->
            insertV1(db, id = "a", title = "Banana bread", clippedAt = 100L, deletedAt = null)
            insertV1(db, id = "b", title = "Soup", clippedAt = 200L, deletedAt = 300L)
            db.execSQL("INSERT INTO pictures (recipeId, position, path) VALUES ('a', 0, '/p/a/001.jpg'), ('a', 1, '/p/a/002.jpg'), ('b', 0, '/p/b/001.jpg')")
        }

        helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT id, title, description, servings, prepMinutes, cookMinutes, totalMinutes, ingredients, steps, notes, sourceUrl, sourceHost, truncated, clippedAt, checkedIngredients, deletedAt, picturesFailed, pictureCandidates, isFavourite, editedAt, origin FROM recipes ORDER BY id").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("a", c.getString(0))
                assertEquals("Banana bread", c.getString(1))
                assertEquals("desc", c.getString(2))
                assertEquals("4", c.getString(3))
                assertEquals(10, c.getInt(4))
                assertEquals(20, c.getInt(5))
                assertEquals(30, c.getInt(6))
                assertEquals("[\"flour\",\"eggs\"]", c.getString(7))
                assertEquals("[\"Mix\",\"Bake\"]", c.getString(8))
                assertEquals("note", c.getString(9))
                assertEquals("https://example.com/a", c.getString(10))
                assertEquals("example.com", c.getString(11))
                assertEquals(1, c.getInt(12))
                assertEquals(100L, c.getLong(13))
                assertEquals("[1]", c.getString(14))
                assertTrue(c.isNull(15))
                assertEquals(1, c.getInt(16))
                assertEquals("[\"https://example.com/x.jpg\"]", c.getString(17))
                assertEquals(0, c.getInt(18))
                assertTrue(c.isNull(19))
                assertEquals("CLIPPED", c.getString(20))

                assertTrue(c.moveToNext())
                assertEquals("b", c.getString(0))
                assertEquals(300L, c.getLong(15))
                assertEquals(0, c.getInt(18))
                assertEquals("CLIPPED", c.getString(20))
            }
            db.query("SELECT recipeId, position, path FROM pictures ORDER BY recipeId, position").use { c ->
                assertEquals(3, c.count)
            }
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_recipes_isFavourite'").use { c ->
                assertEquals(1, c.count)
            }
        }
    }

    private fun insertV1(db: SupportSQLiteDatabase, id: String, title: String, clippedAt: Long, deletedAt: Long?) {
        db.execSQL(
            """INSERT INTO recipes (id, title, description, servings, prepMinutes, cookMinutes, totalMinutes, ingredients, steps, notes,
                   sourceUrl, sourceHost, truncated, clippedAt, checkedIngredients, deletedAt, picturesFailed, pictureCandidates)
               VALUES (?, ?, 'desc', '4', 10, 20, 30, '["flour","eggs"]', '["Mix","Bake"]', 'note',
                   ?, 'example.com', 1, ?, '[1]', ?, 1, '["https://example.com/x.jpg"]')""",
            arrayOf<Any?>(id, title, "https://example.com/$id", clippedAt, deletedAt),
        )
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
