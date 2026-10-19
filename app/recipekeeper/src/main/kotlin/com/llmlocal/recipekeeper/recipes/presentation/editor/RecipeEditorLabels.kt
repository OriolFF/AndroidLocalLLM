package com.llmlocal.recipekeeper.recipes.presentation.editor

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.llmlocal.recipekeeper.R

/**
 * Every user-visible string of the recipe editor (create + edit). Fields
 * have no defaults: the English copy lives in `strings_recipekeeper.xml`
 * and is built by [rememberRecipeEditorLabels] / [recipeEditorLabels].
 * Passed as a plain parameter down the composable tree, like
 * `RecipeKeeperLibraryLabels`.
 */
@Immutable
data class RecipeEditorLabels(
    val editTitle: String,
    val newTitle: String,
    val close: String,
    val save: String,
    val saving: String,
    val titleLabel: String,
    val titleRequired: String,
    val descriptionLabel: String,
    val servingsLabel: String,
    val prepLabel: String,
    val cookLabel: String,
    val totalLabel: String,
    val totalDerived: (minutes: Int) -> String,
    val ingredientsHeader: String,
    val ingredientsHint: String,
    val ingredientLabel: (position: Int) -> String,
    val ingredientPlaceholder: String,
    val addIngredient: String,
    val removeIngredient: (position: Int) -> String,
    val reorderIngredient: (position: Int) -> String,
    val stepsHeader: String,
    val stepsHint: String,
    val stepLabel: (position: Int) -> String,
    val stepPlaceholder: String,
    val addStep: String,
    val removeStep: (position: Int) -> String,
    val reorderStep: (position: Int) -> String,
    val moveUp: String,
    val moveDown: String,
    val needContent: String,
    val notesLabel: String,
    val picturesHeader: String,
    val addPicture: String,
    val removePicture: (index: Int, total: Int) -> String,
    val pictureDescription: (index: Int, total: Int) -> String,
    val useAsCover: String,
    val coverBadge: String,
    val picturesLimit: (max: Int) -> String,
    val pictureFailed: String,
    val sourceReadOnly: (host: String) -> String,
    val sourceReadOnlyHint: String,
    val discardTitle: String,
    val discardMessageEdit: String,
    val discardMessageNew: String,
    val discard: String,
    val keepEditing: String,
    val saveFailed: String,
    val recipeNotFound: String,
)

/** Resource-built [RecipeEditorLabels], refreshed when the configuration (locale) changes. */
@Composable
fun rememberRecipeEditorLabels(): RecipeEditorLabels {
    val configuration = LocalConfiguration.current
    val resources = LocalContext.current.resources
    return remember(configuration) { recipeEditorLabels(resources) }
}

/** Non-composable factory for callers outside composition. */
fun recipeEditorLabels(resources: Resources): RecipeEditorLabels = RecipeEditorLabels(
    editTitle = resources.getString(R.string.recipekeeper_editor_title_edit),
    newTitle = resources.getString(R.string.recipekeeper_editor_title_new),
    close = resources.getString(R.string.recipekeeper_editor_close),
    save = resources.getString(R.string.recipekeeper_editor_save),
    saving = resources.getString(R.string.recipekeeper_editor_saving),
    titleLabel = resources.getString(R.string.recipekeeper_editor_title_label),
    titleRequired = resources.getString(R.string.recipekeeper_editor_title_required),
    descriptionLabel = resources.getString(R.string.recipekeeper_editor_description_label),
    servingsLabel = resources.getString(R.string.recipekeeper_editor_servings_label),
    prepLabel = resources.getString(R.string.recipekeeper_editor_prep_label),
    cookLabel = resources.getString(R.string.recipekeeper_editor_cook_label),
    totalLabel = resources.getString(R.string.recipekeeper_editor_total_label),
    totalDerived = { minutes -> resources.getString(R.string.recipekeeper_editor_total_derived, minutes) },
    ingredientsHeader = resources.getString(R.string.recipekeeper_ingredients_header),
    ingredientsHint = resources.getString(R.string.recipekeeper_editor_ingredients_hint),
    ingredientLabel = { position -> resources.getString(R.string.recipekeeper_editor_ingredient_label, position) },
    ingredientPlaceholder = resources.getString(R.string.recipekeeper_editor_ingredient_placeholder),
    addIngredient = resources.getString(R.string.recipekeeper_editor_add_ingredient),
    removeIngredient = { position -> resources.getString(R.string.recipekeeper_editor_remove_ingredient, position) },
    reorderIngredient = { position -> resources.getString(R.string.recipekeeper_editor_reorder_ingredient, position) },
    stepsHeader = resources.getString(R.string.recipekeeper_steps_header),
    stepsHint = resources.getString(R.string.recipekeeper_editor_steps_hint),
    stepLabel = { position -> resources.getString(R.string.recipekeeper_editor_step_label, position) },
    stepPlaceholder = resources.getString(R.string.recipekeeper_editor_step_placeholder),
    addStep = resources.getString(R.string.recipekeeper_editor_add_step),
    removeStep = { position -> resources.getString(R.string.recipekeeper_editor_remove_step, position) },
    reorderStep = { position -> resources.getString(R.string.recipekeeper_editor_reorder_step, position) },
    moveUp = resources.getString(R.string.recipekeeper_editor_move_up),
    moveDown = resources.getString(R.string.recipekeeper_editor_move_down),
    needContent = resources.getString(R.string.recipekeeper_editor_need_content),
    notesLabel = resources.getString(R.string.recipekeeper_notes_header),
    picturesHeader = resources.getString(R.string.recipekeeper_pictures_header),
    addPicture = resources.getString(R.string.recipekeeper_editor_add_picture),
    removePicture = { index, total -> resources.getString(R.string.recipekeeper_editor_remove_picture, index, total) },
    pictureDescription = { index, total -> resources.getString(R.string.recipekeeper_picture_description, index, total) },
    useAsCover = resources.getString(R.string.recipekeeper_editor_use_as_cover),
    coverBadge = resources.getString(R.string.recipekeeper_editor_cover_badge),
    picturesLimit = { max -> resources.getString(R.string.recipekeeper_editor_pictures_limit, max) },
    pictureFailed = resources.getString(R.string.recipekeeper_editor_picture_failed),
    sourceReadOnly = { host -> resources.getString(R.string.recipekeeper_editor_source_read_only, host) },
    sourceReadOnlyHint = resources.getString(R.string.recipekeeper_editor_source_read_only_hint),
    discardTitle = resources.getString(R.string.recipekeeper_discard_title),
    discardMessageEdit = resources.getString(R.string.recipekeeper_discard_message_edit),
    discardMessageNew = resources.getString(R.string.recipekeeper_discard_message_new),
    discard = resources.getString(R.string.recipekeeper_discard),
    keepEditing = resources.getString(R.string.recipekeeper_keep_editing),
    saveFailed = resources.getString(R.string.recipekeeper_editor_save_failed),
    recipeNotFound = resources.getString(R.string.recipekeeper_recipe_not_found),
)
