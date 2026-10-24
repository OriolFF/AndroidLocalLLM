package com.llmlocal.feature.webclipper.presentation.images

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.feature.webclipper.presentation.WebClipperLabels
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Entry point for the images source (prepare + review).
 *
 * Launches the system photo picker itself when nothing was handed over:
 * `PickMultipleVisualMedia(maxItems)` with ordered selection (the order the
 * user taps is the reading order), or the single-pick contract when one slot
 * is left (the multiple contract needs ≥ 2). A cancelled first pick closes
 * silently; a cancelled "Add more" keeps the review. System back = Close.
 * Snackbars (skipped / capped pictures) go to [onShowSnackbar] when set, else
 * to [snackbarHostState]; the screen shows its own host only when
 * [snackbarHostState] is non-null.
 *
 * @param pendingUris pictures handed over by the share receiver, or `null`
 *   when the screen should open the picker itself
 * @param onNavigateToExtraction called with the images candidate id once the
 *   pictures are in `ClipSession`
 * @param onClosed called when the user leaves without extracting
 */
@Composable
fun <T : Any> WebClipperImagesRoute(
    config: WebClipConfig<T>,
    labels: WebClipperLabels,
    pendingUris: List<String>?,
    onNavigateToExtraction: (candidateId: String) -> Unit,
    onClosed: () -> Unit,
    onNavigateToModelManagement: () -> Unit,
    snackbarHostState: SnackbarHostState?,
    onShowSnackbar: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val viewModel: ImagesClipViewModel = koinViewModel {
        if (pendingUris != null) parametersOf(config, labels, pendingUris) else parametersOf(config, labels)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var pickerMax by remember { mutableIntStateOf(config.imagePolicy.maxImages) }

    fun picked(uris: List<android.net.Uri>) = viewModel.onIntent(
        if (uris.isEmpty()) ImagesClipIntent.PickerCancelled else ImagesClipIntent.ImagesPicked(uris.map { it.toString() }),
    )

    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        picked(listOfNotNull(uri))
    }
    val multiContract = remember(pickerMax) { ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxOf(pickerMax, 2)) }
    val multiLauncher = rememberLauncherForActivityResult(multiContract) { uris -> picked(uris) }

    val request = remember {
        PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
            .setOrderedSelection(true)
            .build()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ImagesClipEffect.LaunchPicker -> {
                    pickerMax = config.imagePolicy.maxImages
                    if (pickerMax > 1) multiLauncher.launch(request) else singleLauncher.launch(request)
                }
                is ImagesClipEffect.LaunchPickerFor -> {
                    pickerMax = effect.remaining
                    if (effect.remaining > 1) multiLauncher.launch(request) else singleLauncher.launch(request)
                }
                is ImagesClipEffect.NavigateToExtraction -> onNavigateToExtraction(effect.candidateId)
                ImagesClipEffect.Closed -> onClosed()
                ImagesClipEffect.NavigateToModelManagement -> onNavigateToModelManagement()
                is ImagesClipEffect.Notify -> {
                    val message = when (effect.kind) {
                        ImagesNotice.SKIPPED -> labels.imagesSkipped(viewModel.state.value.skipped)
                        ImagesNotice.CAPPED -> labels.imagesCapped(viewModel.state.value.maxImages)
                    }
                    if (onShowSnackbar != null) {
                        onShowSnackbar(message)
                    } else {
                        // Launched, not awaited: showSnackbar suspends until it is dismissed.
                        snackbarHostState?.let { host -> scope.launch { host.showSnackbar(message) } }
                    }
                }
            }
        }
    }

    BackHandler { viewModel.onIntent(ImagesClipIntent.Close) }

    ImagesReviewScreen(
        state = state,
        labels = labels,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}
