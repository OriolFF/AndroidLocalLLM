package com.llmlocal.feature.webclipper.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.feature.webclipper.WebClipperLabels
import com.llmlocal.feature.webclipper.mvi.ClipFabState

/**
 * The clip button: one `ExtendedFloatingActionButton` whose icon, label,
 * container and semantics follow [ClipFabState] (UX spec §2 table). One
 * accessibility node: the label is the content description, the count /
 * progress / "unavailable" reason goes into `stateDescription`.
 *
 * Long-press (and the TalkBack long-click action) opens a one-item menu,
 * "Hide on this page" (`labels.fabHideForPage`), when [onHideForPage] is
 * given; the browser then docks a "Show clip button" chip instead.
 *
 * @param expanded extended (label visible) vs. collapsed circle; the
 *   browser collapses it while the page scrolls down
 * @param onHideForPage called when the user picks "Hide on this page";
 *   `null` disables the long-press
 */
@Composable
fun ClipFab(
    state: ClipFabState,
    expanded: Boolean,
    labels: WebClipperLabels,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onHideForPage: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val label = state.label(labels)
    val stateText = state.stateDescription(labels)
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val containerColor = when (state) {
        is ClipFabState.ModelNotReady -> scheme.secondaryContainer
        ClipFabState.PageLoading -> scheme.primaryContainer.copy(alpha = DISABLED_ALPHA)
        is ClipFabState.Found -> scheme.primary
        else -> scheme.primaryContainer
    }
    val contentColor = when (state) {
        ClipFabState.PageLoading -> scheme.onPrimaryContainer.copy(alpha = DISABLED_ALPHA)
        else -> contentColorFor(containerColor)
    }

    Box(modifier = modifier) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            expanded = expanded,
            containerColor = containerColor,
            contentColor = contentColor,
            icon = { FabIcon(state = state, tint = contentColor) },
            text = {
                Text(
                    text = label,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            },
            modifier = Modifier
                .then(
                    if (onHideForPage != null) {
                        Modifier.longPressBeforeClick {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        }
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    if (stateText != null) stateDescription = stateText
                    if (state == ClipFabState.PageLoading) disabled()
                    if (onHideForPage != null) {
                        onLongClick(label = labels.fabHideForPage) {
                            onHideForPage()
                            true
                        }
                    }
                    traversalIndex = -1f
                },
        )
        if (onHideForPage != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(labels.fabHideForPage) },
                    leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onHideForPage()
                    },
                )
            }
        }
    }
}

/**
 * Long-press detector that runs in the *Initial* pointer pass, i.e. before
 * the FAB's own `clickable`. `ExtendedFloatingActionButton` has no
 * `onLongClick`; a plain `detectTapGestures` on the outer modifier would run
 * after the inner click in the Main pass and the release would still fire
 * `onClick`. Once the long-press timeout elapses, [onLongPress] fires and the
 * rest of the gesture is consumed here so the inner click is cancelled.
 */
private fun Modifier.longPressBeforeClick(onLongPress: () -> Unit): Modifier =
    pointerInput(onLongPress) {
        val timeoutMillis = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // `null` only on timeout: an up or a cancel returns a non-null marker.
            val finishedBeforeTimeout: Any? = withTimeoutOrNull(timeoutMillis) {
                waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: Unit
            }
            if (finishedBeforeTimeout == null) {
                onLongPress()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }

@Composable
private fun FabIcon(state: ClipFabState, tint: Color) {
    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it::class },
        label = "clipFabIcon",
    ) { target ->
        when (target) {
            is ClipFabState.ModelNotReady -> {
                val progress = target.download?.takeIf { it.state == DownloadState.RUNNING }?.fraction()
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = tint,
                    )
                } else {
                    Icon(Icons.Outlined.Memory, contentDescription = null, tint = tint)
                }
            }
            ClipFabState.Scanning -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = tint,
            )
            is ClipFabState.Found -> BadgedBox(
                badge = { Badge { Text(target.count.toString()) } },
            ) {
                Icon(Icons.Outlined.ContentCut, contentDescription = null, tint = tint)
            }
            ClipFabState.Extracting -> Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = tint)
            ClipFabState.PageLoading, ClipFabState.Idle ->
                Icon(Icons.Outlined.ContentCut, contentDescription = null, tint = tint)
        }
    }
}

/** Visible label for the state. */
fun ClipFabState.label(labels: WebClipperLabels): String = when (this) {
    is ClipFabState.ModelNotReady -> {
        val percent = download?.takeIf { it.state == DownloadState.RUNNING }?.fraction()?.let { (it * 100).toInt() }
        if (percent != null) labels.fabDownloading(percent) else labels.fabChooseModel
    }
    ClipFabState.PageLoading, ClipFabState.Idle -> labels.fabClip
    ClipFabState.Scanning -> labels.fabScanning
    is ClipFabState.Found -> labels.fabFound(count)
    ClipFabState.Extracting -> labels.fabExtracting
}

/** Extra TalkBack state (count, progress, unavailable reason), or `null`. */
fun ClipFabState.stateDescription(labels: WebClipperLabels): String? = when (this) {
    ClipFabState.PageLoading -> labels.fabUnavailableWhileLoading
    is ClipFabState.Found -> labels.fabFound(count)
    is ClipFabState.ModelNotReady -> download?.takeIf { it.state == DownloadState.RUNNING }
        ?.fraction()?.let { labels.fabDownloading((it * 100).toInt()) }
    else -> null
}

private fun com.llmlocal.core.llm.download.PerModelProgress.fraction(): Float? {
    val total = totalBytes ?: return null
    if (total <= 0) return null
    return (bytesRead.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

private const val DISABLED_ALPHA = 0.38f
