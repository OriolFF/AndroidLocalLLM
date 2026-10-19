package com.llmlocal.recipekeeper.recipes.domain

/**
 * Editor draft of a recipe. `id == null` means a new recipe is being
 * created. Line and picture keys are stable for one editor session so
 * lists can animate and reorder without losing identity.
 *
 * Named `RecipeEdit` (not `RecipeDraft`, which is the streaming parser
 * output in `clip/domain`).
 *
 * @property servings free text, never parsed
 * @property sourceUrl read-only; empty for [RecipeOrigin.CREATED]
 * @property sourceHost read-only; empty for [RecipeOrigin.CREATED]
 */
data class RecipeEdit(
    val id: String?,
    val origin: RecipeOrigin,
    val title: String = "",
    val description: String = "",
    val servings: String = "",
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val ingredients: List<EditableLine> = emptyList(),
    val steps: List<EditableLine> = emptyList(),
    val notes: String = "",
    val pictures: List<EditorPicture> = emptyList(),
    val sourceUrl: String = "",
    val sourceHost: String = "",
)

/**
 * One ingredient or step row in the editor.
 *
 * @property sourceIndex index in the saved recipe's list this row came from,
 *   `null` for rows added in the editor; used to remap ticked ingredients on save
 */
data class EditableLine(val key: Long, val text: String, val sourceIndex: Int? = null)

/** [path] is a temp file while [isNew], else the stored file under `filesDir/recipes/<id>/`. */
data class EditorPicture(val key: Long, val path: String, val isNew: Boolean)

/** The two reorderable lists of the editor. */
enum class EditSection { INGREDIENTS, STEPS }

/** Validation failures reported by `RecipeEditValidator` / `SaveResult.Invalid`. */
sealed interface EditorError {
    data object TitleRequired : EditorError
    data object NeedContent : EditorError
}

/** Outcome of `SaveRecipeUseCase`. */
sealed interface SaveResult {
    data class Saved(val id: String) : SaveResult
    data class Invalid(val errors: Set<EditorError>) : SaveResult
    data class Failed(val cause: Throwable) : SaveResult
}
