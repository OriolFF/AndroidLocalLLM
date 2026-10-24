package com.llmlocal.feature.webclipper.presentation.images

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.EmptyState
import com.llmlocal.core.designsystem.component.GradientHero
import com.llmlocal.core.designsystem.component.SkeletonLine
import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability
import com.llmlocal.feature.webclipper.presentation.WebClipperLabels

/** Widest the info rows and the primary button grow on tablets and foldables. */
private val ContentMaxWidth = 720.dp

/** Tiles per row: ≈ 2 on a phone, 3 at 600dp, 4 at 840dp. */
private val TileMinWidth = 148.dp

/**
 * Prepare + review screen of the images source (UX spec §2.3–§2.4), stateless.
 *
 * Own `Scaffold(contentWindowInsets = safeDrawing)`: the grid scrolls under
 * the bottom bar through `contentPadding`, and "Extract recipe" sits in the
 * `bottomBar` so it stays above the gesture bar. A single picture shows the
 * same screen (the ViewModel auto-extracts a fresh single pick before it is
 * ever seen). While preparing, skeleton tiles stand in for the pictures.
 *
 * Accessibility: tiles carry `Picture i of n` + selected state and custom
 * actions (see [ImageTile]); after a move one invisible polite live region
 * announces the new position; skeletons are invisible to services.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesReviewScreen(
    state: ImagesClipState,
    labels: WebClipperLabels,
    onIntent: (ImagesClipIntent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    var announcement by remember { mutableStateOf("") }
    val direction = LocalLayoutDirection.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(labels.imagesTitle) },
                navigationIcon = {
                    IconButton(onClick = { onIntent(ImagesClipIntent.Close) }) {
                        Icon(Icons.Outlined.Close, contentDescription = labels.cancel)
                    }
                },
            )
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        bottomBar = {
            ExtractBar(
                enabled = state.canExtract && !state.isPreparing,
                label = labels.extractFromImages,
                onClick = { onIntent(ImagesClipIntent.Extract) },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // One polite live region: the position after a move.
            Text(
                text = announcement,
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(TileMinWidth),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp + innerPadding.calculateStartPadding(direction),
                    end = 16.dp + innerPadding.calculateEndPadding(direction),
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                reviewItems(state, labels, onIntent) { announcement = it }
            }
        }
    }
}

private fun LazyGridScope.reviewItems(
    state: ImagesClipState,
    labels: WebClipperLabels,
    onIntent: (ImagesClipIntent) -> Unit,
    announce: (String) -> Unit,
) {
    gate(state, labels, onIntent)

    val noneReadable = !state.isPreparing && state.images.isEmpty() && state.skipped > 0
    if (noneReadable) {
        full("none-readable") {
            EmptyState(
                icon = Icons.Outlined.BrokenImage,
                title = labels.imagesNoneReadableTitle,
                message = labels.imagesNoneReadableMessage,
                actionLabel = labels.pickDifferentImages,
                onAction = { onIntent(ImagesClipIntent.AddMore) },
            )
        }
        return
    }

    full("heading") {
        HeadingBlock(
            title = if (state.isPreparing) labels.imagesPreparing else labels.imagesOrderTitle,
            hint = labels.imagesOrderHint.takeIf { !state.isPreparing && state.needsReview },
        )
    }

    if (state.isPreparing) {
        items(PREPARING_TILES, key = { "skeleton-$it" }) { SkeletonTile() }
        return
    }

    val count = state.images.size
    items(state.images, key = { it.key }) { image ->
        val index = state.images.indexOf(image)
        ImageTile(
            image = image,
            index = index,
            count = count,
            selected = image.key == state.selectedKey,
            labels = labels,
            onSelect = { onIntent(ImagesClipIntent.Select(image.key)) },
            onMove = { to ->
                onIntent(ImagesClipIntent.Move(index, to))
                announce(labels.imageTileDescription(to + 1, count))
            },
            onRemove = { onIntent(ImagesClipIntent.Remove(image.key)) },
            modifier = Modifier.animateItem(),
        )
    }
    if (count < state.maxImages) {
        item(key = "add") {
            AddTile(label = labels.addImages, onClick = { onIntent(ImagesClipIntent.AddMore) })
        }
    }

    val selected = state.images.indexOfFirst { it.key == state.selectedKey }
    if (state.needsReview && selected >= 0) {
        full("arrows") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    enabled = selected > 0,
                    onClick = {
                        onIntent(ImagesClipIntent.Move(selected, selected - 1))
                        announce(labels.imageTileDescription(selected, count))
                    },
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = labels.moveImageLeft)
                }
                IconButton(
                    enabled = selected < count - 1,
                    onClick = {
                        onIntent(ImagesClipIntent.Move(selected, selected + 1))
                        announce(labels.imageTileDescription(selected + 2, count))
                    },
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = labels.moveImageRight)
                }
            }
        }
    }

    full("counter") {
        Text(
            text = labels.imagesCount(count, state.maxImages),
            style = MaterialTheme.typography.labelMedium,
            color = if (state.capped) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    full("privacy") { InfoRow(Icons.Outlined.Lock, labels.imagesPrivacyHint) }
    full("time") { InfoRow(Icons.Outlined.Timer, labels.imagesTimeHint) }
}

/** The model gate (share arrives without a usable model): hero or empty state above the grid. */
private fun LazyGridScope.gate(
    state: ImagesClipState,
    labels: WebClipperLabels,
    onIntent: (ImagesClipIntent) -> Unit,
) {
    when (state.availability) {
        ImagesAvailability.NoModel -> full("gate") {
            GradientHero(
                title = labels.modelNotReadySnackbar,
                actionLabel = labels.fabChooseModel,
                onAction = { onIntent(ImagesClipIntent.OpenModelManagement) },
                modifier = Modifier.widthIn(max = ContentMaxWidth),
            )
        }
        ImagesAvailability.ModelLacksVision -> full("gate") {
            EmptyState(
                icon = Icons.Outlined.ImageSearch,
                title = labels.imagesUnsupportedTitle,
                message = labels.imagesUnsupportedMessage,
                actionLabel = labels.openModels,
                onAction = { onIntent(ImagesClipIntent.OpenModelManagement) },
            )
        }
        ImagesAvailability.Available, ImagesAvailability.Disabled -> Unit
    }
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun HeadingBlock(title: String, hint: String?) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.widthIn(max = ContentMaxWidth),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(text, style = MaterialTheme.typography.bodySmall) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.widthIn(max = ContentMaxWidth),
    )
}

@Composable
private fun SkeletonTile() {
    Box(
        modifier = Modifier
            .aspectRatio(3f / 4f)
            .semantics { invisibleToUser() },
    ) {
        SkeletonLine(widthFraction = 1f, height = 96.dp, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun AddTile(label: String, onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    val radius = with(LocalDensity.current) { 12.dp.toPx() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(3f / 4f)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
                    ),
                )
            }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pinned bottom action; pads the gesture bar itself (the scaffold hands the content only its height). */
@Composable
private fun ExtractBar(enabled: Boolean, label: String, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(label)
            }
        }
    }
}

private const val PREPARING_TILES_COUNT = 3
private val PREPARING_TILES = List(PREPARING_TILES_COUNT) { it }
