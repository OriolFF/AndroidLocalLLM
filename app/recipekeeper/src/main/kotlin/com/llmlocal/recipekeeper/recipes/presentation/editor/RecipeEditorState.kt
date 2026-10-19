package com.llmlocal.recipekeeper.recipes.presentation.editor

import androidx.compose.runtime.Immutable
import com.llmlocal.recipekeeper.recipes.domain.EditorError
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit

/** Whether the editor creates a new recipe or edits a saved one. */
enum class EditorMode { CREATE, EDIT }

/**
 * Single source of truth for the editor screen.
 *
 * @property edit the live draft, `null` while loading or when the recipe was not found
 * @property initial the draft as loaded; [isDirty] compares against it
 * @property importingPictures pictures being copied from the picker (skeleton tiles)
 * @property maxPictures cap on the picture strip
 */
@Immutable
data class RecipeEditorState(
    val mode: EditorMode,
    val edit: RecipeEdit? = null,
    val initial: RecipeEdit? = null,
    val isLoading: Boolean = true,
    val errors: Set<EditorError> = emptySet(),
    val showDiscardDialog: Boolean = false,
    val isSaving: Boolean = false,
    val importingPictures: Int = 0,
    val maxPictures: Int = 8,
) {
    /** The draft differs from what was loaded. */
    val isDirty: Boolean
        get() = edit != null && edit != initial

    /** Edit mode for an id that is not in the library. */
    val notFound: Boolean
        get() = !isLoading && edit == null
}
