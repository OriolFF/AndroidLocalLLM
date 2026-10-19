package com.llmlocal.recipekeeper.recipes.presentation.editor

import com.llmlocal.recipekeeper.recipes.domain.EditSection

/** One-shot side effects from [RecipeEditorViewModel], consumed by `RecipeEditorRoute`. */
sealed interface RecipeEditorEffect {
    /** Saved; the host navigates ([mode] decides where). */
    data class Saved(val id: String, val mode: EditorMode) : RecipeEditorEffect

    /** Nothing to confirm (or discard confirmed): pop the editor. */
    data object Closed : RecipeEditorEffect

    /** Move keyboard focus to the row [key] of [section]. */
    data class FocusLine(val section: EditSection, val key: Long) : RecipeEditorEffect
    data object FocusTitle : RecipeEditorEffect
    data class ScrollTo(val target: EditorTarget) : RecipeEditorEffect

    /** A transient message; the route maps [kind] to a label (rule 0). */
    data class Notify(val kind: EditorNotice) : RecipeEditorEffect
}

enum class EditorNotice { SAVE_FAILED, PICTURE_FAILED, PICTURES_LIMIT }

enum class EditorTarget { TITLE, INGREDIENTS }
