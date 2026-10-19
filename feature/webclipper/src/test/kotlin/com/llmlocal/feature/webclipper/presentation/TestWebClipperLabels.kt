package com.llmlocal.feature.webclipper.presentation

/** Full English label set for unit tests (no Android resources on the JVM). */
fun testWebClipperLabels(
    searchUrlTemplate: (String) -> String = { q -> "https://search.example/?q=$q" },
) = WebClipperLabels(
    // Browser top bar
    browserBackToLibrary = "Back to library",
    urlFieldHint = "Search or enter a site",
    urlFieldDescription = "Address bar",
    reloadPage = "Reload",
    stopLoading = "Stop loading",
    moreOptions = "More options",
    goForward = "Forward",
    sharePage = "Share page",
    openExternal = "Open in browser",
    copyLink = "Copy link",
    linkCopied = "Link copied",
    searchUrlTemplate = searchUrlTemplate,
    pageLoadError = "This page couldn't be loaded",
    scanFailed = "Couldn't read this page",

    // Clip button
    fabClip = "Clip recipe",
    fabScanning = "Scanning page…",
    fabFound = { n -> if (n == 1) "Recipe found" else "$n recipes found" },
    fabExtracting = "Extracting…",
    fabChooseModel = "Choose a model",
    fabDownloading = { p -> "Downloading $p%" },
    fabUnavailableWhileLoading = "Unavailable until the page finishes loading",
    waitForPage = "Wait for the page to finish loading",
    fabHideForPage = "Hide on this page",
    showClipButton = "Show clip button",
    modelNotReadySnackbar = "Choose and download a model first",

    // Candidate picker
    candidatesTitle = "Which recipe?",
    candidatesSubtitle = { n, h -> "$n recipes found on $h" },
    candidateMetaTemplate = { i, m ->
        listOfNotNull(i?.let { "$it ingredients" }, m?.let { "$it min" }).joinToString(" · ")
    },
    sourceStructured = "Structured",
    sourceText = "Text",
    candidateThumbnailDescription = { t -> "Picture of $t" },
    nothingDetectedTitle = "No recipe found on this page",
    nothingDetectedMessage =
        "Open the recipe itself — list, search and category pages don't contain the ingredients.",
    extractAnyway = "Extract anyway",
    keepBrowsing = "Keep browsing",
    notThisOne = "Not this one?",

    // Extraction
    extractionTitle = "Clipping recipe",
    cancelExtraction = "Cancel",
    stopExtraction = "Stop",
    phaseReading = "Reading page",
    phaseExtracting = "Extracting recipe",
    phasePictures = "Saving pictures",
    phaseSaving = "Saving to library",
    extractingHint = "Running on your phone — this takes a moment",
    extractedWithoutModel = "Read directly from the page's recipe data",
    elapsedTemplate = { s -> "${s}s" },
    keepAppOpenHint = "Keep the app open until it finishes",
    keepPicture = "Keep this picture",
    showRawOutput = "Show raw output",
    hideRawOutput = "Hide raw output",
    viewRecipe = "View recipe",
    clipCancelled = "Clip cancelled",
    recipeSaved = "Saved to your library",
    modelFailedTitle = "The model couldn't run",
    parseFailedTitle = "Couldn't read a recipe from this page",
    parseFailedMessage = "The page text was sent, but no ingredients or steps came back.",
    truncatedWarning = "The recipe may be incomplete",
    tryAgain = "Try again",
    tryWholePage = "Try the whole page",
    backToPage = "Back to page",
    openModels = "Open models",
    picturesUnavailable = "Pictures unavailable",
    retryPictures = "Retry pictures",

    // Duplicate warning (shown before the model runs when the page was clipped before)
    duplicateTitle = "Already in your library",
    duplicateMessage = "You clipped this page before. Replace the saved recipe with a fresh clip?",
    replace = "Replace",
    keepBoth = "Keep both",
    cancel = "Cancel",
)
