package com.llmlocal.feature.webclipper.presentation.images

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.llmlocal.core.designsystem.component.AppLoadingIndicator
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.feature.webclipper.presentation.WebClipperLabels

/**
 * Entry point for the images source (prepare + review). Placeholder until
 * stream I2: renders the app loading indicator and is unreachable while the
 * images row is hidden.
 *
 * @param pendingUris pictures handed over by the share receiver, or `null`
 *   when the screen should open the picker itself
 * @param onNavigateToExtraction called with the images candidate id
 * @param onClosed called when the user leaves without extracting
 */
@Suppress("UNUSED_PARAMETER")
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
    AppLoadingIndicator(modifier = modifier)
}
