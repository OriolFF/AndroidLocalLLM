package com.llmlocal.feature.webclipper.presentation

import androidx.compose.runtime.Immutable

/**
 * Every user-visible string the web-clipper feature renders (browser top
 * bar, clip button, candidate picker, extraction screen).
 *
 * Library / detail / delete copy is app-owned and lives next to the app's
 * own screens. No field has a default: build one from resources with
 * [webClipperLabels] / [defaultWebClipperLabels] (brand overrides replace
 * the same-named string resources); passed as a plain parameter
 * to [WebClipperBrowserRoute] and [WebClipperExtractionRoute].
 */
@Immutable
data class WebClipperLabels(
    // Browser top bar
    val browserBackToLibrary: String,
    val urlFieldHint: String,
    val urlFieldDescription: String,
    val reloadPage: String,
    val stopLoading: String,
    val moreOptions: String,
    val goForward: String,
    val sharePage: String,
    val openExternal: String,
    val copyLink: String,
    val linkCopied: String,
    val searchUrlTemplate: (String) -> String,
    val pageLoadError: String,
    val scanFailed: String,

    // Clip button
    val fabClip: String,
    val fabScanning: String,
    val fabFound: (Int) -> String,
    val fabExtracting: String,
    val fabChooseModel: String,
    val fabDownloading: (Int) -> String,
    val fabUnavailableWhileLoading: String,
    val waitForPage: String,
    val fabHideForPage: String,
    val showClipButton: String,
    val modelNotReadySnackbar: String,

    // Candidate picker
    val candidatesTitle: String,
    val candidatesSubtitle: (Int, String) -> String,
    val candidateMetaTemplate: (Int?, Int?) -> String,
    val sourceStructured: String,
    val sourceText: String,
    val candidateThumbnailDescription: (String) -> String,
    val nothingDetectedTitle: String,
    val nothingDetectedMessage: String,
    val extractAnyway: String,
    val keepBrowsing: String,
    val notThisOne: String,

    // Extraction
    val extractionTitle: String,
    val cancelExtraction: String,
    val stopExtraction: String,
    val phaseReading: String,
    val phaseExtracting: String,
    val phasePictures: String,
    val phaseSaving: String,
    val extractingHint: String,
    val extractedWithoutModel: String,
    val elapsedTemplate: (Int) -> String,
    val keepAppOpenHint: String,
    val keepPicture: String,
    val showRawOutput: String,
    val hideRawOutput: String,
    val viewRecipe: String,
    val clipCancelled: String,
    val recipeSaved: String,
    val modelFailedTitle: String,
    val parseFailedTitle: String,
    val parseFailedMessage: String,
    val truncatedWarning: String,
    val tryAgain: String,
    val tryWholePage: String,
    val backToPage: String,
    val openModels: String,
    val picturesUnavailable: String,
    val retryPictures: String,

    // Duplicate warning (shown before the model runs when the page was clipped before)
    val duplicateTitle: String,
    val duplicateMessage: String,
    val replace: String,
    val keepBoth: String,
    val cancel: String,

    // Images source
    val imagesTitle: String,
    val imagesPreparing: String,
    val imagesOrderTitle: String,
    val imagesOrderHint: String,
    val imagesCount: (Int, Int) -> String,
    val imageTileDescription: (Int, Int) -> String,
    val imageSelected: String,
    val moveImageLeft: String,
    val moveImageRight: String,
    val removeImage: (Int, Int) -> String,
    val addImages: String,
    val imagesPrivacyHint: String,
    val imagesTimeHint: String,
    val extractFromImages: String,
    val imagesCapped: (Int) -> String,
    val imagesSkipped: (Int) -> String,
    val imagesNoneReadableTitle: String,
    val imagesNoneReadableMessage: String,
    val pickDifferentImages: String,
    val imagesSourceStrip: (Int) -> String,
    val phaseReadingImages: String,
    val imagesExtractingHint: String,
    val imagesNoRecipeTitle: String,
    val imagesNoRecipeMessage: String,
    val imagesUnsupportedTitle: String,
    val imagesUnsupportedMessage: String,
    val backToLibrary: String,
    val clipAlreadyRunning: String,
)
