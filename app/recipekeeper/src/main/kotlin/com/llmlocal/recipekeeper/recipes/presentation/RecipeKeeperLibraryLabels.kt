package com.llmlocal.recipekeeper.recipes.presentation

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.llmlocal.recipekeeper.R

/**
 * Every user-visible string for Recipe Keeper's app-owned screens — the
 * library grid, recipe detail, the streamed draft card and the
 * delete / share / open-original flows.
 *
 * Web-clipper copy (browser top bar, clip FAB, candidate picker, extraction
 * phases) is owned by [RecipeKeeperClipperLabels] /
 * [com.llmlocal.feature.webclipper.WebClipperLabels]. Model-management copy
 * is owned by [RecipeKeeperModelManagementLabels].
 *
 * Fields have no defaults: the English copy lives in `strings_recipekeeper.xml`
 * and is built by [rememberRecipeKeeperLibraryLabels] / [recipeKeeperLibraryLabels].
 * Passed as a plain parameter down the
 * composable tree (same convention as `LlmTaskLabels`); there is no
 * composition local.
 */
@Immutable
data class RecipeKeeperLibraryLabels(
    // ---- App chrome ------------------------------------------------------
    val libraryTitle: String,
    val modelsTitle: String,
    val manageModelsDescription: String,
    val back: String,

    // ---- Library screen --------------------------------------------------
    val libraryFabClip: String,
    val libraryEmptyTitle: String,
    val libraryEmptyMessage: String,
    val openBrowser: String,
    val searchHint: String,
    val searchActionDescription: String,
    val closeSearch: String,
    val clearSearch: String,
    val searchEmptyTitle: (String) -> String,
    val recipeTileDescription: (title: String, host: String) -> String,
    val recipeMenuDescription: String,
    /** Separator between the parts of a tile's meta line (host · time). */
    val metaSeparator: String,
    val libraryNoModelTitle: String,
    val libraryNoModelSubtitle: String,
    val chooseModelAction: String,
    val downloadingModel: (name: String) -> String,
    /** Spoken description of the download card: "<downloadingModel>, <percent>%". */
    val downloadingModelDescription: (title: String, percent: Int) -> String,
    val tapToManageModels: String,

    // ---- Detail screen ---------------------------------------------------
    val shareRecipe: String,
    val moreOptions: String,
    val servingsTemplate: (Int) -> String,
    /** Yield the source wrote as free text ("6–8 cookies"); shown verbatim. */
    val servingsFreeform: (String) -> String,
    val prepTemplate: (Int) -> String,
    val cookTemplate: (Int) -> String,
    val totalTemplate: (Int) -> String,
    val minutesTemplate: (Int) -> String,
    val clippedOn: (date: String) -> String,
    val openOriginal: String,
    val ingredientsHeader: String,
    val stepsHeader: String,
    val notesHeader: String,
    val picturesHeader: String,
    val resetChecks: String,
    val checkedCount: (checked: Int, total: Int) -> String,
    val stepDescription: (index: Int, total: Int) -> String,
    /** Spoken description of a step row: its position, then its text. */
    val stepSpokenDescription: (position: String, text: String) -> String,
    val pictureDescription: (index: Int, total: Int) -> String,
    val keepScreenOn: String,
    val reExtract: String,
    val heroImageDescription: (title: String) -> String,
    val shareTemplate: (title: String, url: String) -> String,
    val incompleteChip: String,
    val recipeNotFound: String,
    /** Chip shown when no picture could be downloaded at clip time (offline). */
    val picturesUnavailable: String,
    val retryPictures: String,
    val retryPicturesFailed: String,

    // ---- Streamed draft card --------------------------------------------
    val draftWaiting: String,
    val draftTitleFound: (title: String) -> String,
    val draftIngredientsFound: (count: Int) -> String,
    val draftStepsFound: (count: Int) -> String,

    // ---- Delete / undo ---------------------------------------------------
    val delete: String,
    val cancel: String,
    val deleteTitle: String,
    val deleteMessage: String,
    val recipeDeleted: String,
    val undo: String,
    val undoDeleteFailed: String,
)

/** Resource-built [RecipeKeeperLibraryLabels], refreshed when the configuration (locale) changes. */
@Composable
fun rememberRecipeKeeperLibraryLabels(): RecipeKeeperLibraryLabels {
    val configuration = LocalConfiguration.current
    val resources = LocalContext.current.resources
    return remember(configuration) { recipeKeeperLibraryLabels(resources) }
}

/** Non-composable factory for callers outside composition (e.g. the share-text template in DI). */
fun recipeKeeperLibraryLabels(resources: Resources): RecipeKeeperLibraryLabels = RecipeKeeperLibraryLabels(
    libraryTitle = resources.getString(R.string.recipekeeper_library_title),
    modelsTitle = resources.getString(R.string.recipekeeper_models_title),
    manageModelsDescription = resources.getString(R.string.recipekeeper_manage_models_description),
    back = resources.getString(R.string.recipekeeper_back),
    libraryFabClip = resources.getString(R.string.recipekeeper_open_browser),
    libraryEmptyTitle = resources.getString(R.string.recipekeeper_library_empty_title),
    libraryEmptyMessage = resources.getString(R.string.recipekeeper_library_empty_message),
    openBrowser = resources.getString(R.string.recipekeeper_open_browser),
    searchHint = resources.getString(R.string.recipekeeper_search_hint),
    searchActionDescription = resources.getString(R.string.recipekeeper_search_action_description),
    closeSearch = resources.getString(R.string.recipekeeper_search_close),
    clearSearch = resources.getString(R.string.recipekeeper_search_clear),
    searchEmptyTitle = { q -> resources.getString(R.string.recipekeeper_search_empty_title, q) },
    recipeTileDescription = { t, h -> resources.getString(R.string.recipekeeper_tile_description, t, h) },
    recipeMenuDescription = resources.getString(R.string.recipekeeper_recipe_menu_description),
    metaSeparator = resources.getString(R.string.recipekeeper_meta_separator),
    libraryNoModelTitle = resources.getString(R.string.recipekeeper_library_no_model_title),
    libraryNoModelSubtitle = resources.getString(R.string.recipekeeper_library_no_model_subtitle),
    chooseModelAction = resources.getString(R.string.recipekeeper_choose_model_action),
    downloadingModel = { n -> resources.getString(R.string.recipekeeper_downloading_model, n) },
    downloadingModelDescription = { t, p -> resources.getString(R.string.recipekeeper_downloading_model_description, t, p) },
    tapToManageModels = resources.getString(R.string.recipekeeper_tap_to_manage_models),
    shareRecipe = resources.getString(R.string.recipekeeper_share_recipe),
    moreOptions = resources.getString(R.string.recipekeeper_more_options),
    servingsTemplate = { n -> resources.getQuantityString(R.plurals.recipekeeper_servings, n, n) },
    servingsFreeform = { s -> s },
    prepTemplate = { m -> resources.getString(R.string.recipekeeper_prep_minutes, m) },
    cookTemplate = { m -> resources.getString(R.string.recipekeeper_cook_minutes, m) },
    totalTemplate = { m -> resources.getString(R.string.recipekeeper_total_minutes, m) },
    minutesTemplate = { m -> resources.getString(R.string.recipekeeper_minutes, m) },
    clippedOn = { d -> resources.getString(R.string.recipekeeper_clipped_on, d) },
    openOriginal = resources.getString(R.string.recipekeeper_open_original),
    ingredientsHeader = resources.getString(R.string.recipekeeper_ingredients_header),
    stepsHeader = resources.getString(R.string.recipekeeper_steps_header),
    notesHeader = resources.getString(R.string.recipekeeper_notes_header),
    picturesHeader = resources.getString(R.string.recipekeeper_pictures_header),
    resetChecks = resources.getString(R.string.recipekeeper_reset_checks),
    checkedCount = { c, t -> resources.getString(R.string.recipekeeper_checked_count, c, t) },
    stepDescription = { i, t -> resources.getString(R.string.recipekeeper_step_description, i, t) },
    pictureDescription = { i, t -> resources.getString(R.string.recipekeeper_picture_description, i, t) },
    stepSpokenDescription = { p, t -> resources.getString(R.string.recipekeeper_step_spoken_description, p, t) },
    keepScreenOn = resources.getString(R.string.recipekeeper_keep_screen_on),
    reExtract = resources.getString(R.string.recipekeeper_re_extract),
    heroImageDescription = { t -> resources.getString(R.string.recipekeeper_hero_image_description, t) },
    shareTemplate = { t, u -> resources.getString(R.string.recipekeeper_share_template, t, u) },
    incompleteChip = resources.getString(R.string.recipekeeper_incomplete_chip),
    recipeNotFound = resources.getString(R.string.recipekeeper_recipe_not_found),
    picturesUnavailable = resources.getString(R.string.recipekeeper_pictures_unavailable),
    retryPictures = resources.getString(R.string.recipekeeper_retry_pictures),
    retryPicturesFailed = resources.getString(R.string.recipekeeper_retry_pictures_failed),
    draftWaiting = resources.getString(R.string.recipekeeper_draft_waiting),
    draftTitleFound = { t -> resources.getString(R.string.recipekeeper_draft_title_found, t) },
    draftIngredientsFound = { n -> resources.getQuantityString(R.plurals.recipekeeper_draft_ingredients_found, n, n) },
    draftStepsFound = { n -> resources.getQuantityString(R.plurals.recipekeeper_draft_steps_found, n, n) },
    delete = resources.getString(R.string.recipekeeper_delete),
    cancel = resources.getString(R.string.recipekeeper_cancel),
    deleteTitle = resources.getString(R.string.recipekeeper_delete_title),
    deleteMessage = resources.getString(R.string.recipekeeper_delete_message),
    recipeDeleted = resources.getString(R.string.recipekeeper_recipe_deleted),
    undo = resources.getString(R.string.recipekeeper_undo),
    undoDeleteFailed = resources.getString(R.string.recipekeeper_undo_delete_failed),
)
