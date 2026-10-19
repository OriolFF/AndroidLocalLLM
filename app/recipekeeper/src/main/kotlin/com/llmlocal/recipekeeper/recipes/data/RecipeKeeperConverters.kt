package com.llmlocal.recipekeeper.recipes.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONException

/**
 * Room type converters: `List<String>` and `Set<Int>` ↔ JSON array text.
 * JSON (rather than a delimiter) so ingredient text containing any
 * character round-trips, and so the `ingredients` column stays searchable
 * with `LIKE`.
 */
class RecipeKeeperConverters {

    @TypeConverter
    fun stringListToJson(value: List<String>): String = JSONArray(value).toString()

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = runCatching {
        val array = JSONArray(value)
        List(array.length()) { array.optString(it) }
    }.getOrElse { emptyList() }

    @TypeConverter
    fun intSetToJson(value: Set<Int>): String = JSONArray(value.sorted()).toString()

    @TypeConverter
    fun jsonToIntSet(value: String): Set<Int> = try {
        val array = JSONArray(value)
        buildSet { for (i in 0 until array.length()) add(array.getInt(i)) }
    } catch (_: JSONException) {
        emptySet()
    }
}
