package com.llmlocal.feature.webclipper

import android.net.Uri
import androidx.compose.runtime.Immutable

/**
 * Every user-visible string the web-clipper feature renders (browser top
 * bar, clip button, candidate picker, extraction screen).
 *
 * Library / detail / delete copy is app-owned and lives next to the app's
 * own screens. Each field carries the English default from the UX spec so
 * an app only overrides what its brand needs; passed as a plain parameter
 * to [WebClipperBrowserRoute] and [WebClipperExtractionRoute].
 */
@Immutable
data class WebClipperLabels(
    // Browser top bar
    val browserBackToLibrary: String = "Back to library",
    val urlFieldHint: String = "Search or enter a site",
    val urlFieldDescription: String = "Address bar",
    val reloadPage: String = "Reload",
    val stopLoading: String = "Stop loading",
    val moreOptions: String = "More options",
    val goForward: String = "Forward",
    val sharePage: String = "Share page",
    val openExternal: String = "Open in browser",
    val copyLink: String = "Copy link",
    val linkCopied: String = "Link copied",
    val searchUrlTemplate: (String) -> String = { q -> "https://duckduckgo.com/?q=${Uri.encode(q)}" },
    val pageLoadError: String = "This page couldn't be loaded",
    val scanFailed: String = "Couldn't read this page",

    // Clip button
    val fabClip: String = "Clip recipe",
    val fabScanning: String = "Scanning page…",
    val fabFound: (Int) -> String = { n -> if (n == 1) "Recipe found" else "$n recipes found" },
    val fabExtracting: String = "Extracting…",
    val fabChooseModel: String = "Choose a model",
    val fabDownloading: (Int) -> String = { p -> "Downloading $p%" },
    val fabUnavailableWhileLoading: String = "Unavailable until the page finishes loading",
    val waitForPage: String = "Wait for the page to finish loading",
    val fabHideForPage: String = "Hide on this page",
    val showClipButton: String = "Show clip button",
    val modelNotReadySnackbar: String = "Choose and download a model first",

    // Candidate picker
    val candidatesTitle: String = "Which recipe?",
    val candidatesSubtitle: (Int, String) -> String = { n, h -> "$n recipes found on $h" },
    val candidateMetaTemplate: (Int?, Int?) -> String = { i, m ->
        listOfNotNull(i?.let { "$it ingredients" }, m?.let { "$it min" }).joinToString(" · ")
    },
    val sourceStructured: String = "Structured",
    val sourceText: String = "Text",
    val candidateThumbnailDescription: (String) -> String = { t -> "Picture of $t" },
    val nothingDetectedTitle: String = "No recipe found on this page",
    val nothingDetectedMessage: String =
        "Open the recipe itself — list, search and category pages don't contain the ingredients.",
    val extractAnyway: String = "Extract anyway",
    val keepBrowsing: String = "Keep browsing",
    val notThisOne: String = "Not this one?",

    // Extraction
    val extractionTitle: String = "Clipping recipe",
    val cancelExtraction: String = "Cancel",
    val stopExtraction: String = "Stop",
    val phaseReading: String = "Reading page",
    val phaseExtracting: String = "Extracting recipe",
    val phasePictures: String = "Saving pictures",
    val phaseSaving: String = "Saving to library",
    val extractingHint: String = "Running on your phone — this takes a moment",
    val extractedWithoutModel: String = "Read directly from the page's recipe data",
    val elapsedTemplate: (Int) -> String = { s -> "${s}s" },
    val keepAppOpenHint: String = "Keep the app open until it finishes",
    val keepPicture: String = "Keep this picture",
    val showRawOutput: String = "Show raw output",
    val hideRawOutput: String = "Hide raw output",
    val viewRecipe: String = "View recipe",
    val clipCancelled: String = "Clip cancelled",
    val recipeSaved: String = "Saved to your library",
    val modelFailedTitle: String = "The model couldn't run",
    val parseFailedTitle: String = "Couldn't read a recipe from this page",
    val parseFailedMessage: String = "The page text was sent, but no ingredients or steps came back.",
    val truncatedWarning: String = "The recipe may be incomplete",
    val tryAgain: String = "Try again",
    val tryWholePage: String = "Try the whole page",
    val backToPage: String = "Back to page",
    val openModels: String = "Open models",
    val picturesUnavailable: String = "Pictures unavailable",
    val retryPictures: String = "Retry pictures",

    // Duplicate warning (shown before the model runs when the page was clipped before)
    val duplicateTitle: String = "Already in your library",
    val duplicateMessage: String = "You clipped this page before. Replace the saved recipe with a fresh clip?",
    val replace: String = "Replace",
    val keepBoth: String = "Keep both",
    val cancel: String = "Cancel",
)
