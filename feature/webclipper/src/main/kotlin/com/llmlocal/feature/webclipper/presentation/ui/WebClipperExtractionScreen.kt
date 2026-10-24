package com.llmlocal.feature.webclipper.presentation.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.llmlocal.core.designsystem.component.EmptyState
import com.llmlocal.feature.webclipper.domain.CandidatePayload
import com.llmlocal.feature.webclipper.presentation.WebClipperLabels
import com.llmlocal.feature.webclipper.domain.ExtractionError
import com.llmlocal.feature.webclipper.presentation.mvi.ExtractionIntent
import com.llmlocal.feature.webclipper.presentation.mvi.ExtractionState
import com.llmlocal.feature.webclipper.domain.Phase
import com.llmlocal.core.designsystem.component.PhaseItem
import com.llmlocal.core.designsystem.component.PhaseProgress
import com.llmlocal.core.designsystem.component.PhaseStatus as DsPhaseStatus
import com.llmlocal.core.designsystem.component.SkeletonLine
import com.llmlocal.core.designsystem.component.StreamingCursor
import com.llmlocal.feature.webclipper.domain.PhaseStatus
import com.llmlocal.feature.webclipper.presentation.mvi.PictureDraft
import com.llmlocal.feature.webclipper.domain.PictureStatus

/**
 * Stateless extraction screen (UX spec §4): top bar with Close, source
 * strip, [PhaseProgress], the app's draft slot (or an error state), the
 * picture strip with keep checkboxes and the Stop button.
 *
 * On compact windows the action (Stop while running, "View recipe" once
 * saved) sits in the scaffold's `bottomBar`, so it is reachable without
 * scrolling past the draft (WS10 bug 8). Wide windows keep it in the
 * left column under the phases.
 *
 * Accessibility: phase changes are announced through one invisible polite
 * live region (the streaming card is **not** a live region); the raw tail
 * is hidden from services while running; skeletons are invisible.
 *
 * ≥ 600dp wide windows put the phases in a left column (36 %).
 *
 * @param elapsedSeconds seconds since the extraction started (route-owned ticker)
 * @param renderDraft the app's skeleton-that-fills-in over the raw stream
 * @param onViewSaved "View recipe" after completion
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebClipperExtractionScreen(
    state: ExtractionState,
    labels: WebClipperLabels,
    elapsedSeconds: Int,
    renderDraft: @Composable (streamedText: String, isRunning: Boolean) -> Unit,
    onIntent: (ExtractionIntent) -> Unit,
    onNavigateToModelManagement: () -> Unit,
    onViewSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activePhase = state.phases.entries.firstOrNull { it.value == PhaseStatus.Active }?.key
    val announcement = when {
        state.error != null -> errorTitle(state.error, labels)
        state.savedId != null -> labels.recipeSaved
        activePhase != null -> phaseLabel(activePhase, labels)
        else -> ""
    }

    if (state.duplicateOf != null) {
        DuplicateDialog(labels = labels, onIntent = onIntent)
    }

    BoxWithConstraints(modifier = modifier) {
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        val hasAction = state.isRunning || state.savedId != null
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text(labels.extractionTitle) },
                    navigationIcon = {
                        IconButton(onClick = { onIntent(ExtractionIntent.Cancel) }) {
                            Icon(Icons.Outlined.Close, contentDescription = labels.cancelExtraction)
                        }
                    },
                )
            },
            bottomBar = {
                if (!wide && hasAction) {
                    ActionBar(state, labels, onIntent, onViewSaved)
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
            // One polite live region for milestones only.
            Text(
                text = announcement,
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (wide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(0.36f)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SourceStrip(state, labels, onIntent)
                        PhaseList(state, labels, elapsedSeconds)
                        ActionButton(state, labels, onIntent, onViewSaved)
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.64f)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        bodyItems(state, labels, renderDraft, onIntent, onNavigateToModelManagement)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item("source") { SourceStrip(state, labels, onIntent) }
                    item("phases") { PhaseList(state, labels, elapsedSeconds) }
                    bodyItems(state, labels, renderDraft, onIntent, onNavigateToModelManagement)
                }
            }
            }
        }
    }
}

/**
 * "Already in your library" (WS10 bug 6): shown before the model runs when
 * the sink knows this page. Replace keeps the old id, Keep both saves a
 * second copy, Cancel / dismiss goes back to the page.
 */
@Composable
private fun DuplicateDialog(labels: WebClipperLabels, onIntent: (ExtractionIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(ExtractionIntent.Cancel) },
        title = { Text(labels.duplicateTitle) },
        text = { Text(labels.duplicateMessage) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onIntent(ExtractionIntent.KeepBoth) }) { Text(labels.keepBoth) }
                TextButton(onClick = { onIntent(ExtractionIntent.ReplaceExisting) }) { Text(labels.replace) }
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(ExtractionIntent.Cancel) }) { Text(labels.cancel) }
        },
    )
}

/**
 * Compact-window action dock: Stop while running, "View recipe" once
 * saved. Lives in the scaffold's `bottomBar`, so it pads the navigation
 * bar itself (the scaffold hands the content only the bar's height).
 */
@Composable
private fun ActionBar(
    state: ExtractionState,
    labels: WebClipperLabels,
    onIntent: (ExtractionIntent) -> Unit,
    onViewSaved: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ActionButton(state, labels, onIntent, onViewSaved)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.bodyItems(
    state: ExtractionState,
    labels: WebClipperLabels,
    renderDraft: @Composable (String, Boolean) -> Unit,
    onIntent: (ExtractionIntent) -> Unit,
    onNavigateToModelManagement: () -> Unit,
) {
    if (state.truncated) {
        item("truncated") { TruncatedBanner(labels) }
    }
    val error = state.error
    if (error != null) {
        item("error") { ErrorBlock(state, error, labels, onIntent, onNavigateToModelManagement) }
    } else {
        item("draft") {
            Box(modifier = Modifier.animateContentSize()) {
                renderDraft(state.streamedText, state.isRunning)
            }
        }
        if (state.streamedText.isNotBlank()) {
            item("raw") { RawOutput(state, labels, onIntent) }
        }
    }
    if (state.pictures.isNotEmpty()) {
        item("pictures") { PictureStrip(state.pictures, labels, onIntent) }
    }
}

@Composable
private fun SourceStrip(
    state: ExtractionState,
    labels: WebClipperLabels,
    onIntent: (ExtractionIntent) -> Unit,
) {
    val source = listOfNotNull(
        state.sourceHost?.takeIf { it.isNotBlank() },
        (state.pageTitle ?: state.candidate.title).takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = source,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (state.otherCandidates > 0) {
            TextButton(onClick = { onIntent(ExtractionIntent.BackToCandidates) }) {
                Text(labels.notThisOne)
            }
        }
    }
}

@Composable
private fun PhaseList(state: ExtractionState, labels: WebClipperLabels, elapsedSeconds: Int) {
    val items = Phase.entries.map { phase ->
        val status = state.phases[phase] ?: PhaseStatus.Pending
        val detail = when {
            phase == Phase.Extracting && status == PhaseStatus.Active ->
                "${labels.extractingHint} · ${labels.elapsedTemplate(elapsedSeconds)}\n${labels.keepAppOpenHint}"
            phase == Phase.Extracting && status == PhaseStatus.Warning -> labels.truncatedWarning
            phase == Phase.Extracting && status == PhaseStatus.Done && state.extractedWithoutModel -> labels.extractedWithoutModel
            phase == Phase.Pictures && status == PhaseStatus.Warning -> labels.picturesUnavailable
            else -> null
        }
        PhaseItem(label = phaseLabel(phase, labels), status = status.toDesign(), detail = detail)
    }
    PhaseProgress(items = items)
}

@Composable
private fun TruncatedBanner(labels: WebClipperLabels) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
            Text(labels.truncatedWarning, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RawOutput(
    state: ExtractionState,
    labels: WebClipperLabels,
    onIntent: (ExtractionIntent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { onIntent(ExtractionIntent.ToggleRaw) }) {
            Text(if (state.showRaw) labels.hideRawOutput else labels.showRawOutput)
        }
        if (state.showRaw) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .padding(12.dp)
                        .then(if (state.isRunning) Modifier.clearAndSetSemantics {} else Modifier),
                ) {
                    Text(
                        text = state.streamedText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (state.isRunning) StreamingCursor(modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun ErrorBlock(
    state: ExtractionState,
    error: ExtractionError,
    labels: WebClipperLabels,
    onIntent: (ExtractionIntent) -> Unit,
    onNavigateToModelManagement: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (error) {
            is ExtractionError.ModelFailed -> {
                EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = labels.modelFailedTitle,
                    message = error.reason,
                    actionLabel = labels.tryAgain,
                    onAction = { onIntent(ExtractionIntent.Retry) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onIntent(ExtractionIntent.Cancel) }) { Text(labels.backToPage) }
                    TextButton(onClick = onNavigateToModelManagement) { Text(labels.openModels) }
                }
            }
            is ExtractionError.ParseFailed -> {
                EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = labels.parseFailedTitle,
                    message = labels.parseFailedMessage,
                    actionLabel = labels.tryAgain,
                    onAction = { onIntent(ExtractionIntent.Retry) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onIntent(ExtractionIntent.Cancel) }) { Text(labels.backToPage) }
                    if (state.candidate.payload !is CandidatePayload.WholePage) {
                        TextButton(onClick = { onIntent(ExtractionIntent.RetryWholePage) }) { Text(labels.tryWholePage) }
                    }
                }
                if (error.rawOutput.isNotBlank()) {
                    RawOutput(state = state.copy(streamedText = error.rawOutput, isRunning = false), labels, onIntent)
                }
            }
            ExtractionError.ImagesUnsupported -> {
                EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = labels.imagesUnsupportedTitle,
                    message = labels.imagesUnsupportedMessage,
                    actionLabel = labels.openModels,
                    onAction = onNavigateToModelManagement,
                )
                TextButton(onClick = { onIntent(ExtractionIntent.Cancel) }) { Text(labels.backToPage) }
            }
            ExtractionError.ModelMissing -> {
                EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = labels.modelFailedTitle,
                    message = labels.modelNotReadySnackbar,
                    actionLabel = labels.openModels,
                    onAction = onNavigateToModelManagement,
                )
                TextButton(onClick = { onIntent(ExtractionIntent.Cancel) }) { Text(labels.backToPage) }
            }
        }
    }
}

@Composable
private fun PictureStrip(
    pictures: List<PictureDraft>,
    labels: WebClipperLabels,
    onIntent: (ExtractionIntent) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(pictures, key = { it.url }) { picture ->
            PictureTile(picture = picture, labels = labels, onToggle = { onIntent(ExtractionIntent.TogglePicture(picture.url)) })
        }
    }
}

@Composable
private fun PictureTile(
    picture: PictureDraft,
    labels: WebClipperLabels,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceContainerHigh)
            .toggleable(
                value = picture.keep,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics { contentDescription = labels.keepPicture },
    ) {
        when (picture.status) {
            PictureStatus.Done -> AsyncImage(
                model = picture.localFile ?: picture.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            PictureStatus.Failed -> Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
            PictureStatus.Pending, PictureStatus.Downloading -> {
                SkeletonLine(widthFraction = 1f, modifier = Modifier.fillMaxSize(), height = 96.dp)
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center),
                    strokeWidth = 2.dp,
                )
            }
        }
        Checkbox(
            checked = picture.keep,
            onCheckedChange = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp),
        )
    }
}

@Composable
private fun ActionButton(
    state: ExtractionState,
    labels: WebClipperLabels,
    onIntent: (ExtractionIntent) -> Unit,
    onViewSaved: () -> Unit,
) {
    when {
        state.isRunning -> OutlinedButton(
            onClick = { onIntent(ExtractionIntent.Cancel) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.StopCircle, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(labels.stopExtraction)
        }
        state.savedId != null -> Button(
            onClick = onViewSaved,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(labels.viewRecipe)
        }
        else -> Spacer(modifier = Modifier.height(0.dp))
    }
}

/** Visible label for [phase]. */
fun phaseLabel(phase: Phase, labels: WebClipperLabels): String = when (phase) {
    Phase.Reading -> labels.phaseReading
    Phase.Extracting -> labels.phaseExtracting
    Phase.Pictures -> labels.phasePictures
    Phase.Saving -> labels.phaseSaving
}

private fun errorTitle(error: ExtractionError, labels: WebClipperLabels): String = when (error) {
    is ExtractionError.ModelFailed, ExtractionError.ModelMissing -> labels.modelFailedTitle
    is ExtractionError.ParseFailed -> labels.parseFailedTitle
    ExtractionError.ImagesUnsupported -> labels.imagesUnsupportedTitle
}

/** The feature's MVI phase status maps 1:1 onto the design-system marker set. */
private fun PhaseStatus.toDesign(): DsPhaseStatus = when (this) {
    PhaseStatus.Pending -> DsPhaseStatus.Pending
    PhaseStatus.Active -> DsPhaseStatus.Active
    PhaseStatus.Done -> DsPhaseStatus.Done
    PhaseStatus.Failed -> DsPhaseStatus.Failed
    PhaseStatus.Warning -> DsPhaseStatus.Warning
}
