package com.llmlocal.recipekeeper.config

import com.llmlocal.feature.webclipper.WebClipperLabels

/**
 * Recipe Keeper's copy for the shared web-clipper feature (browser top
 * bar, clip FAB, candidate picker, extraction phases).
 *
 * The feature's English defaults already speak about recipes, so only
 * the lines where the brand voice differs are overridden here. Passed to
 * `WebClipperBrowserRoute` / `WebClipperExtractionRoute` from `MainActivity`.
 */
val RecipeKeeperClipperLabels: WebClipperLabels = WebClipperLabels(
    browserBackToLibrary = "Back to your recipes",
    recipeSaved = "Saved to your recipes",
    extractingHint = "Reading the recipe on your phone — this takes a moment",
)
