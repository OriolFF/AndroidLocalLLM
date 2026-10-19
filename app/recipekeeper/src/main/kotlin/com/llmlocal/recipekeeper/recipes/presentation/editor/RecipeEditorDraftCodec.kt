package com.llmlocal.recipekeeper.recipes.presentation.editor

import com.llmlocal.recipekeeper.recipes.domain.EditableLine
import com.llmlocal.recipekeeper.recipes.domain.EditorPicture
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialises the editor draft (and the snapshot it is compared against) to a
 * single JSON string so it fits in a `SavedStateHandle` and survives process
 * death. Pictures are only paths; no image bytes enter the bundle.
 */
internal object RecipeEditorDraftCodec {

    fun encode(edit: RecipeEdit, initial: RecipeEdit?): String = JSONObject().apply {
        put("edit", edit.toJson())
        initial?.let { put("initial", it.toJson()) }
    }.toString()

    /** @return `edit` and `initial`, or `null` when [json] cannot be read */
    fun decode(json: String): Pair<RecipeEdit, RecipeEdit?>? = runCatching {
        val root = JSONObject(json)
        root.getJSONObject("edit").toEdit() to root.optJSONObject("initial")?.toEdit()
    }.getOrNull()

    private fun RecipeEdit.toJson() = JSONObject().apply {
        put("id", id ?: JSONObject.NULL)
        put("origin", origin.name)
        put("title", title)
        put("description", description)
        put("servings", servings)
        put("prep", prepMinutes ?: JSONObject.NULL)
        put("cook", cookMinutes ?: JSONObject.NULL)
        put("total", totalMinutes ?: JSONObject.NULL)
        put("ingredients", ingredients.toJson())
        put("steps", steps.toJson())
        put("notes", notes)
        put("pictures", JSONArray().also { array ->
            pictures.forEach { array.put(JSONObject().put("key", it.key).put("path", it.path).put("isNew", it.isNew)) }
        })
        put("sourceUrl", sourceUrl)
        put("sourceHost", sourceHost)
    }

    private fun List<EditableLine>.toJson() = JSONArray().also { array ->
        forEach {
            array.put(
                JSONObject().put("key", it.key).put("text", it.text).put("sourceIndex", it.sourceIndex ?: JSONObject.NULL),
            )
        }
    }

    private fun JSONObject.toEdit() = RecipeEdit(
        id = if (isNull("id")) null else getString("id"),
        origin = RecipeOrigin.entries.firstOrNull { it.name == optString("origin") } ?: RecipeOrigin.CLIPPED,
        title = optString("title"),
        description = optString("description"),
        servings = optString("servings"),
        prepMinutes = optIntOrNull("prep"),
        cookMinutes = optIntOrNull("cook"),
        totalMinutes = optIntOrNull("total"),
        ingredients = getJSONArray("ingredients").toLines(),
        steps = getJSONArray("steps").toLines(),
        notes = optString("notes"),
        pictures = getJSONArray("pictures").let { array ->
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                EditorPicture(o.getLong("key"), o.getString("path"), o.getBoolean("isNew"))
            }
        },
        sourceUrl = optString("sourceUrl"),
        sourceHost = optString("sourceHost"),
    )

    private fun JSONArray.toLines() = (0 until length()).map {
        val o = getJSONObject(it)
        EditableLine(o.getLong("key"), o.getString("text"), o.optIntOrNull("sourceIndex"))
    }

    private fun JSONObject.optIntOrNull(name: String): Int? = if (isNull(name)) null else getInt(name)
}
