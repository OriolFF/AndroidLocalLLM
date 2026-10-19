package com.llmlocal.recipekeeper.recipes.presentation.editor

import com.llmlocal.recipekeeper.recipes.domain.EditSection

/** User actions on the editor screen. Minutes arrive as raw text; the ViewModel parses and caps them. */
sealed interface RecipeEditorIntent {
    data class TitleChanged(val value: String) : RecipeEditorIntent
    data class DescriptionChanged(val value: String) : RecipeEditorIntent
    data class ServingsChanged(val value: String) : RecipeEditorIntent
    data class PrepChanged(val value: String) : RecipeEditorIntent
    data class CookChanged(val value: String) : RecipeEditorIntent
    data class TotalChanged(val value: String) : RecipeEditorIntent
    data class NotesChanged(val value: String) : RecipeEditorIntent

    /** A line's text changed; a value containing `\n` is a paste and is split into rows. */
    data class LineChanged(val section: EditSection, val key: Long, val text: String) : RecipeEditorIntent

    /** Adds an empty row after [afterKey], or at the end when `null`. */
    data class AddLine(val section: EditSection, val afterKey: Long?) : RecipeEditorIntent
    data class RemoveLine(val section: EditSection, val key: Long) : RecipeEditorIntent
    data class MoveLine(val section: EditSection, val from: Int, val to: Int) : RecipeEditorIntent

    /** Photo picker result; each uri is imported into the editor's temp folder. */
    data class PicturesPicked(val uris: List<String>) : RecipeEditorIntent
    data class RemovePicture(val key: Long) : RecipeEditorIntent

    /** Moves the picture to the front (the cover). */
    data class SetCover(val key: Long) : RecipeEditorIntent

    data object Save : RecipeEditorIntent

    /** Close / back: asks for confirmation when the draft is dirty. */
    data object RequestClose : RecipeEditorIntent
    data object DismissDiscard : RecipeEditorIntent
    data object ConfirmDiscard : RecipeEditorIntent
}
