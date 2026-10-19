package com.llmlocal.recipekeeper.recipes.presentation

import androidx.compose.runtime.Immutable

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
 * Every field carries the English default from UX spec §7, so a rebrand
 * only overrides the values it needs. Passed as a plain parameter down the
 * composable tree (same convention as `LlmTaskLabels`); there is no
 * composition local.
 */
@Immutable
data class RecipeKeeperLibraryLabels(
    // ---- App chrome ------------------------------------------------------
    val libraryTitle: String = "Your recipes",
    val modelsTitle: String = "Models",
    val manageModelsDescription: String = "Manage models",
    val back: String = "Back",

    // ---- Library screen --------------------------------------------------
    val libraryFabClip: String = "Clip a recipe",
    val libraryEmptyTitle: String = "No recipes yet",
    val libraryEmptyMessage: String =
        "Browse any cooking site and tap Clip. The recipe is extracted " +
            "on your phone and saved here.",
    val openBrowser: String = "Open browser",
    val searchHint: String = "Search recipes or ingredients",
    val searchActionDescription: String = "Search",
    val closeSearch: String = "Close search",
    val clearSearch: String = "Clear search",
    val searchEmptyTitle: (String) -> String = { q -> "Nothing matches “$q”" },
    val recipeTileDescription: (title: String, host: String) -> String = { t, h -> "$t, from $h" },
    val recipeMenuDescription: String = "Recipe options",
    val libraryNoModelTitle: String = "Choose a model to start clipping",
    val libraryNoModelSubtitle: String =
        "Recipes are extracted on your phone by a local language model. " +
            "Pick one in Models; it works offline afterwards.",
    val chooseModelAction: String = "Open models",
    val downloadingModel: (name: String) -> String = { n -> "Downloading $n…" },
    val tapToManageModels: String = "Tap to manage models",

    // ---- Detail screen ---------------------------------------------------
    val shareRecipe: String = "Share recipe",
    val moreOptions: String = "More options",
    val servingsTemplate: (Int) -> String = { n -> if (n == 1) "1 serving" else "$n servings" },
    /** Yield the source wrote as free text ("6–8 cookies"); shown verbatim. */
    val servingsFreeform: (String) -> String = { s -> s },
    val prepTemplate: (Int) -> String = { m -> "Prep $m min" },
    val cookTemplate: (Int) -> String = { m -> "Cook $m min" },
    val totalTemplate: (Int) -> String = { m -> "$m min total" },
    val minutesTemplate: (Int) -> String = { m -> "$m min" },
    val clippedOn: (date: String) -> String = { d -> "Clipped $d" },
    val openOriginal: String = "Open original",
    val ingredientsHeader: String = "Ingredients",
    val stepsHeader: String = "Steps",
    val notesHeader: String = "Notes",
    val picturesHeader: String = "Pictures",
    val resetChecks: String = "Reset",
    val checkedCount: (checked: Int, total: Int) -> String = { c, t -> "$c of $t checked" },
    val stepDescription: (index: Int, total: Int) -> String = { i, t -> "Step $i of $t" },
    val pictureDescription: (index: Int, total: Int) -> String = { i, t -> "Picture $i of $t" },
    val keepScreenOn: String = "Keep screen on",
    val reExtract: String = "Re-extract from source",
    val heroImageDescription: (title: String) -> String = { t -> "Photo of $t" },
    val shareTemplate: (title: String, url: String) -> String = { t, u -> "$t\n$u" },
    val incompleteChip: String = "Possibly incomplete",
    val recipeNotFound: String = "This recipe is no longer in your library",
    /** Chip shown when no picture could be downloaded at clip time (offline). */
    val picturesUnavailable: String = "Pictures unavailable",
    val retryPictures: String = "Retry pictures",
    val retryPicturesFailed: String = "Still couldn't download the pictures — check your connection",

    // ---- Streamed draft card --------------------------------------------
    val draftWaiting: String = "Waiting for the first words from the model…",
    val draftTitleFound: (title: String) -> String = { t -> "Recipe found: $t" },
    val draftIngredientsFound: (count: Int) -> String = { n ->
        if (n == 1) "1 ingredient found" else "$n ingredients found"
    },
    val draftStepsFound: (count: Int) -> String = { n -> if (n == 1) "1 step found" else "$n steps found" },

    // ---- Delete / undo ---------------------------------------------------
    val delete: String = "Delete",
    val cancel: String = "Cancel",
    val deleteTitle: String = "Delete this recipe?",
    val deleteMessage: String = "Its saved pictures are removed too.",
    val recipeDeleted: String = "Recipe deleted",
    val undo: String = "Undo",
    val undoDeleteFailed: String = "Too late to undo — the recipe was already deleted",
)

/** The brand-default English [RecipeKeeperLibraryLabels] used by the app and its previews. */
val DefaultRecipeKeeperLibraryLabels: RecipeKeeperLibraryLabels = RecipeKeeperLibraryLabels()
