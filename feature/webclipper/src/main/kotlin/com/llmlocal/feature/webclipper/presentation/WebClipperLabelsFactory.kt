package com.llmlocal.feature.webclipper.presentation

import android.content.res.Resources
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.llmlocal.feature.webclipper.R

/** Search-engine URL for text typed into the address bar (a URL, not copy). */
fun defaultSearchUrlTemplate(query: String): String = "https://duckduckgo.com/?q=${Uri.encode(query)}"

/**
 * Builds [WebClipperLabels] from this module's `strings_webclipper.xml`.
 * A brand overrides copy by declaring string resources with the same names
 * in its app module (resource merging lets the app win).
 */
fun webClipperLabels(
    resources: Resources,
    searchUrlTemplate: (String) -> String = ::defaultSearchUrlTemplate,
): WebClipperLabels = WebClipperLabels(
        browserBackToLibrary = resources.getString(R.string.webclipper_browser_back_to_library),
        urlFieldHint = resources.getString(R.string.webclipper_browser_url_field_hint),
        urlFieldDescription = resources.getString(R.string.webclipper_browser_url_field_description),
        reloadPage = resources.getString(R.string.webclipper_browser_reload_page),
        stopLoading = resources.getString(R.string.webclipper_browser_stop_loading),
        moreOptions = resources.getString(R.string.webclipper_browser_more_options),
        goForward = resources.getString(R.string.webclipper_browser_go_forward),
        sharePage = resources.getString(R.string.webclipper_browser_share_page),
        openExternal = resources.getString(R.string.webclipper_browser_open_external),
        copyLink = resources.getString(R.string.webclipper_browser_copy_link),
        linkCopied = resources.getString(R.string.webclipper_browser_link_copied),
        searchUrlTemplate = searchUrlTemplate,
        pageLoadError = resources.getString(R.string.webclipper_browser_page_load_error),
        scanFailed = resources.getString(R.string.webclipper_browser_scan_failed),
        fabClip = resources.getString(R.string.webclipper_fab_clip),
        fabScanning = resources.getString(R.string.webclipper_fab_scanning),
        fabFound = { n -> resources.getQuantityString(R.plurals.webclipper_fab_found, n, n) },
        fabExtracting = resources.getString(R.string.webclipper_fab_extracting),
        fabChooseModel = resources.getString(R.string.webclipper_fab_choose_model),
        fabDownloading = { p -> resources.getString(R.string.webclipper_fab_downloading, p) },
        fabUnavailableWhileLoading = resources.getString(R.string.webclipper_fab_unavailable_while_loading),
        waitForPage = resources.getString(R.string.webclipper_fab_wait_for_page),
        fabHideForPage = resources.getString(R.string.webclipper_fab_hide_for_page),
        showClipButton = resources.getString(R.string.webclipper_fab_show_clip_button),
        modelNotReadySnackbar = resources.getString(R.string.webclipper_fab_model_not_ready_snackbar),
        candidatesTitle = resources.getString(R.string.webclipper_candidates_candidates_title),
        candidatesSubtitle = { n, h -> resources.getString(R.string.webclipper_candidates_subtitle, n, h) },
        candidateMetaTemplate = { i, m ->
            listOfNotNull(
                i?.let { resources.getString(R.string.webclipper_candidates_meta_ingredients, it) },
                m?.let { resources.getString(R.string.webclipper_candidates_meta_minutes, it) },
            ).joinToString(resources.getString(R.string.webclipper_candidates_meta_separator))
        },
        sourceStructured = resources.getString(R.string.webclipper_candidates_source_structured),
        sourceText = resources.getString(R.string.webclipper_candidates_source_text),
        candidateThumbnailDescription = { t -> resources.getString(R.string.webclipper_candidates_thumbnail_description, t) },
        nothingDetectedTitle = resources.getString(R.string.webclipper_candidates_nothing_detected_title),
        nothingDetectedMessage = resources.getString(R.string.webclipper_candidates_nothing_detected_message),
        extractAnyway = resources.getString(R.string.webclipper_candidates_extract_anyway),
        keepBrowsing = resources.getString(R.string.webclipper_candidates_keep_browsing),
        notThisOne = resources.getString(R.string.webclipper_candidates_not_this_one),
        extractionTitle = resources.getString(R.string.webclipper_extraction_title),
        cancelExtraction = resources.getString(R.string.webclipper_extraction_cancel_extraction),
        stopExtraction = resources.getString(R.string.webclipper_extraction_stop_extraction),
        phaseReading = resources.getString(R.string.webclipper_extraction_phase_reading),
        phaseExtracting = resources.getString(R.string.webclipper_extraction_phase_extracting),
        phasePictures = resources.getString(R.string.webclipper_extraction_phase_pictures),
        phaseSaving = resources.getString(R.string.webclipper_extraction_phase_saving),
        extractingHint = resources.getString(R.string.webclipper_extraction_extracting_hint),
        extractedWithoutModel = resources.getString(R.string.webclipper_extraction_extracted_without_model),
        elapsedTemplate = { s -> resources.getString(R.string.webclipper_extraction_elapsed, s) },
        keepAppOpenHint = resources.getString(R.string.webclipper_extraction_keep_app_open_hint),
        keepPicture = resources.getString(R.string.webclipper_extraction_keep_picture),
        showRawOutput = resources.getString(R.string.webclipper_extraction_show_raw_output),
        hideRawOutput = resources.getString(R.string.webclipper_extraction_hide_raw_output),
        viewRecipe = resources.getString(R.string.webclipper_extraction_view_recipe),
        clipCancelled = resources.getString(R.string.webclipper_extraction_clip_cancelled),
        recipeSaved = resources.getString(R.string.webclipper_extraction_recipe_saved),
        modelFailedTitle = resources.getString(R.string.webclipper_extraction_model_failed_title),
        parseFailedTitle = resources.getString(R.string.webclipper_extraction_parse_failed_title),
        parseFailedMessage = resources.getString(R.string.webclipper_extraction_parse_failed_message),
        truncatedWarning = resources.getString(R.string.webclipper_extraction_truncated_warning),
        tryAgain = resources.getString(R.string.webclipper_extraction_try_again),
        tryWholePage = resources.getString(R.string.webclipper_extraction_try_whole_page),
        backToPage = resources.getString(R.string.webclipper_extraction_back_to_page),
        openModels = resources.getString(R.string.webclipper_extraction_open_models),
        picturesUnavailable = resources.getString(R.string.webclipper_extraction_pictures_unavailable),
        retryPictures = resources.getString(R.string.webclipper_extraction_retry_pictures),
        duplicateTitle = resources.getString(R.string.webclipper_duplicate_title),
        duplicateMessage = resources.getString(R.string.webclipper_duplicate_message),
        replace = resources.getString(R.string.webclipper_duplicate_replace),
        keepBoth = resources.getString(R.string.webclipper_duplicate_keep_both),
        cancel = resources.getString(R.string.webclipper_duplicate_cancel),
        imagesTitle = resources.getString(R.string.webclipper_images_title),
        imagesPreparing = resources.getString(R.string.webclipper_images_preparing),
        imagesOrderTitle = resources.getString(R.string.webclipper_images_order_title),
        imagesOrderHint = resources.getString(R.string.webclipper_images_order_hint),
        imagesCount = { a, b -> resources.getString(R.string.webclipper_images_count, a, b) },
        imageTileDescription = { a, b -> resources.getString(R.string.webclipper_images_tile_description, a, b) },
        imageSelected = resources.getString(R.string.webclipper_images_tile_selected),
        moveImageLeft = resources.getString(R.string.webclipper_images_move_left),
        moveImageRight = resources.getString(R.string.webclipper_images_move_right),
        removeImage = { a, b -> resources.getString(R.string.webclipper_images_remove, a, b) },
        addImages = resources.getString(R.string.webclipper_images_add_more),
        imagesPrivacyHint = resources.getString(R.string.webclipper_images_privacy_hint),
        imagesTimeHint = resources.getString(R.string.webclipper_images_time_hint),
        extractFromImages = resources.getString(R.string.webclipper_images_extract),
        imagesCapped = { n -> resources.getString(R.string.webclipper_images_capped, n) },
        imagesSkipped = { n -> resources.getQuantityString(R.plurals.webclipper_images_skipped, n, n) },
        imagesNoneReadableTitle = resources.getString(R.string.webclipper_images_none_readable_title),
        imagesNoneReadableMessage = resources.getString(R.string.webclipper_images_none_readable_message),
        pickDifferentImages = resources.getString(R.string.webclipper_images_pick_different),
        imagesSourceStrip = { n -> resources.getQuantityString(R.plurals.webclipper_images_source_strip, n, n) },
        phaseReadingImages = resources.getString(R.string.webclipper_extraction_phase_reading_images),
        imagesExtractingHint = resources.getString(R.string.webclipper_images_extracting_hint),
        imagesNoRecipeTitle = resources.getString(R.string.webclipper_images_no_recipe_title),
        imagesNoRecipeMessage = resources.getString(R.string.webclipper_images_no_recipe_message),
        imagesUnsupportedTitle = resources.getString(R.string.webclipper_images_unsupported_title),
        imagesUnsupportedMessage = resources.getString(R.string.webclipper_images_unsupported_message),
        backToLibrary = resources.getString(R.string.webclipper_extraction_back_to_library),
        clipAlreadyRunning = resources.getString(R.string.webclipper_clip_already_running),
)

/** Brand-neutral labels for feature previews and for apps that want the stock copy. */
@Composable
fun defaultWebClipperLabels(
    searchUrlTemplate: (String) -> String = ::defaultSearchUrlTemplate,
): WebClipperLabels {
    val resources = LocalContext.current.resources
    return remember(resources, searchUrlTemplate) { webClipperLabels(resources, searchUrlTemplate) }
}
