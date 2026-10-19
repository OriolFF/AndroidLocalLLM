package com.llmlocal.recipekeeper.clip.presentation

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.llmlocal.feature.webclipper.presentation.WebClipperLabels
import com.llmlocal.feature.webclipper.presentation.webClipperLabels

/**
 * Recipe Keeper's copy for the shared web-clipper feature (browser top
 * bar, clip FAB, candidate picker, extraction phases).
 *
 * The feature's `strings_webclipper.xml` already speaks about recipes; the
 * lines where the brand voice differs are overridden by same-named strings
 * in this app's `strings_webclipper.xml`, which resource merging prefers.
 * Passed to `WebClipperBrowserRoute` / `WebClipperExtractionRoute`.
 */
fun recipeKeeperClipperLabels(resources: Resources): WebClipperLabels = webClipperLabels(resources)

@Composable
fun rememberRecipeKeeperClipperLabels(): WebClipperLabels {
    val resources = LocalContext.current.resources
    return remember(resources) { recipeKeeperClipperLabels(resources) }
}
