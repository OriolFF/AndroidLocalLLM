package com.llmlocal.recipekeeper.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.llmlocal.core.designsystem.component.AppLoadingIndicator
import com.llmlocal.core.designsystem.component.EmptyState
import com.llmlocal.core.designsystem.component.GradientHero
import com.llmlocal.recipekeeper.config.RecipeKeeperLibraryLabels
import com.llmlocal.core.designsystem.component.NumberBadge
import com.llmlocal.core.designsystem.component.SectionHeader
import com.llmlocal.recipekeeper.domain.ClippedRecipe
import com.llmlocal.recipekeeper.library.IncompleteBadge
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Window width from which the detail splits into ingredients | steps panes. */
private val TwoPaneMinWidth = 600.dp
private val TopBarHeight = 64.dp

/** Bottom hero scrim: flat minimum behind the title band, darker at the edge. */
private const val HERO_SCRIM_MIN_ALPHA = 0.40f
private const val HERO_SCRIM_MAX_ALPHA = 0.82f

/**
 * Stateless recipe detail screen (UX spec §5). Owns its own `Scaffold`.
 *
 * Compact (< 600dp): one `LazyColumn` whose first item is a 16:10 hero
 * photo drawn under the status bar with a bottom scrim and the title on
 * it; a transparent `TopAppBar` floats over it and fades to `surface` as
 * the hero scrolls away. Without a picture the hero is the brand
 * `GradientHero` under a solid bar.
 *
 * Wide (≥ 600dp or landscape tablets): solid bar, two independently
 * scrolling panes — hero + meta + source + ingredients on the left, steps
 * + pictures + notes on the right — so the ingredient list stays in view
 * while cooking through the steps.
 *
 * Everything the cook touches is ≥ 48dp: ingredient rows are
 * `toggleable(role = Checkbox)`, step rows are `selectable` and announce
 * "Step i of n", the pager pages are described as "Picture i of n".
 */
@Composable
fun RecipeDetailScreen(
    state: RecipeDetailState,
    labels: RecipeKeeperLibraryLabels,
    snackbarHostState: SnackbarHostState,
    onIntent: (RecipeDetailIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn(enabled = state.keepScreenOn)

    if (state.showDeleteDialog) {
        DeleteRecipeDialog(
            labels = labels,
            onConfirm = { onIntent(RecipeDetailIntent.ConfirmDelete) },
            onDismiss = { onIntent(RecipeDetailIntent.DismissDelete) },
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val recipe = state.recipe
        val wide = maxWidth >= TwoPaneMinWidth
        when {
            recipe == null -> PlaceholderDetail(
                state = state,
                labels = labels,
                snackbarHostState = snackbarHostState,
                onBack = onBack,
            )
            wide -> WideDetail(
                recipe = recipe,
                state = state,
                labels = labels,
                snackbarHostState = snackbarHostState,
                onIntent = onIntent,
                onBack = onBack,
                heroHeight = minOf(maxWidth / 2 * 9 / 21, 320.dp),
            )
            else -> CompactDetail(
                recipe = recipe,
                state = state,
                labels = labels,
                snackbarHostState = snackbarHostState,
                onIntent = onIntent,
                onBack = onBack,
                heroHeight = if (maxWidth > maxHeight) minOf(maxWidth * 9 / 21, 320.dp) else maxWidth * 10 / 16,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Layouts
// ---------------------------------------------------------------------------

/** Loading / not-found: solid bar, centred content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderDetail(
    state: RecipeDetailState,
    labels: RecipeKeeperLibraryLabels,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = labels.back)
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (state.isLoading) {
                AppLoadingIndicator()
            } else {
                EmptyState(icon = Icons.AutoMirrored.Outlined.MenuBook, title = labels.recipeNotFound)
            }
        }
    }
}

@Composable
private fun CompactDetail(
    recipe: ClippedRecipe,
    state: RecipeDetailState,
    labels: RecipeKeeperLibraryLabels,
    snackbarHostState: SnackbarHostState,
    onIntent: (RecipeDetailIntent) -> Unit,
    onBack: () -> Unit,
    heroHeight: Dp,
) {
    val listState = rememberLazyListState()
    val hasPicture = recipe.pictures.firstOrNull() != null
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barAlpha by rememberBarAlpha(listState = listState, hasPicture = hasPicture)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset + 32.dp),
            ) {
                item(key = "hero") {
                    if (hasPicture) {
                        HeroImage(
                            recipe = recipe,
                            labels = labels,
                            height = heroHeight + topInset,
                            topInset = topInset,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Spacer(modifier = Modifier.height(topInset + TopBarHeight))
                        GradientHero(
                            title = recipe.title,
                            subtitle = recipe.sourceHost,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                item(key = "meta") { MetaChips(recipe = recipe, state = state, labels = labels, onIntent = onIntent) }
                item(key = "source") { SourceRow(recipe = recipe, labels = labels, onIntent = onIntent) }
                ingredientsSection(recipe, labels, onIntent)
                stepsSection(recipe, state.currentStep, labels, onIntent)
                picturesSection(recipe, labels)
                notesSection(recipe, labels)
            }
            DetailTopBar(
                labels = labels,
                backgroundAlpha = barAlpha,
                overImage = hasPicture && barAlpha < 0.5f,
                keepScreenOn = state.keepScreenOn,
                onBack = onBack,
                onIntent = onIntent,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun WideDetail(
    recipe: ClippedRecipe,
    state: RecipeDetailState,
    labels: RecipeKeeperLibraryLabels,
    snackbarHostState: SnackbarHostState,
    onIntent: (RecipeDetailIntent) -> Unit,
    onBack: () -> Unit,
    heroHeight: Dp,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DetailTopBar(
                labels = labels,
                backgroundAlpha = 1f,
                overImage = false,
                keepScreenOn = state.keepScreenOn,
                onBack = onBack,
                onIntent = onIntent,
            )
        },
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .widthIn(max = 1280.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item(key = "hero") {
                    if (recipe.pictures.isNotEmpty()) {
                        HeroImage(
                            recipe = recipe,
                            labels = labels,
                            height = heroHeight,
                            topInset = 0.dp,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else {
                        GradientHero(
                            title = recipe.title,
                            subtitle = recipe.sourceHost,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                item(key = "meta") { MetaChips(recipe = recipe, state = state, labels = labels, onIntent = onIntent) }
                item(key = "source") { SourceRow(recipe = recipe, labels = labels, onIntent = onIntent) }
                ingredientsSection(recipe, labels, onIntent)
            }
            LazyColumn(
                modifier = Modifier.weight(1.1f).fillMaxHeight(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            ) {
                stepsSection(recipe, state.currentStep, labels, onIntent)
                picturesSection(recipe, labels)
                notesSection(recipe, labels)
            }
        }
    }
}

/**
 * 0 while the hero photo is fully visible → 1 once it has scrolled under
 * the bar. Drives the top-bar background and icon tint crossfade.
 */
@Composable
private fun rememberBarAlpha(listState: LazyListState, hasPicture: Boolean): androidx.compose.runtime.State<Float> {
    val density = LocalDensity.current
    val fadeDistancePx = with(density) { 160.dp.toPx() }
    return remember(listState, hasPicture, fadeDistancePx) {
        derivedStateOf {
            when {
                !hasPicture -> 1f
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> (listState.firstVisibleItemScrollOffset / fadeDistancePx).coerceIn(0f, 1f)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar + dialogs
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    labels: RecipeKeeperLibraryLabels,
    backgroundAlpha: Float,
    overImage: Boolean,
    keepScreenOn: Boolean,
    onBack: () -> Unit,
    onIntent: (RecipeDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (overImage) Color.White else scheme.onSurface
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth().background(scheme.surface.copy(alpha = backgroundAlpha))) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = labels.back, tint = tint)
                }
            },
            actions = {
                IconButton(onClick = { onIntent(RecipeDetailIntent.Share) }) {
                    Icon(Icons.Outlined.Share, contentDescription = labels.shareRecipe, tint = tint)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = labels.moreOptions, tint = tint)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(labels.openOriginal) },
                            leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                            onClick = { menuOpen = false; onIntent(RecipeDetailIntent.OpenOriginal) },
                        )
                        DropdownMenuItem(
                            text = { Text(labels.reExtract) },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                            onClick = { menuOpen = false; onIntent(RecipeDetailIntent.ReExtract) },
                        )
                        DropdownMenuItem(
                            text = { Text(labels.keepScreenOn) },
                            leadingIcon = { Icon(Icons.Outlined.ScreenLockPortrait, contentDescription = null) },
                            trailingIcon = {
                                if (keepScreenOn) Icon(Icons.Outlined.Check, contentDescription = null)
                            },
                            onClick = { menuOpen = false; onIntent(RecipeDetailIntent.ToggleKeepScreenOn) },
                            modifier = Modifier.semantics {
                                toggleableState = ToggleableState(keepScreenOn)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(labels.delete, color = scheme.error) },
                            leadingIcon = {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = scheme.error)
                            },
                            onClick = { menuOpen = false; onIntent(RecipeDetailIntent.RequestDelete) },
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun DeleteRecipeDialog(
    labels: RecipeKeeperLibraryLabels,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(labels.deleteTitle) },
        text = { Text(labels.deleteMessage) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(labels.delete, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(labels.cancel) }
        },
    )
}

/** Requests `FLAG_KEEP_SCREEN_ON` while [enabled]; always cleared on dispose. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

// ---------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------

/**
 * Hero photo with a top scrim (keeps the floating icons legible) and a
 * bottom scrim carrying the title in white `headlineMedium`.
 *
 * The bottom scrim is at least 40 % black where the title sits (ramping to
 * 82 % at the bottom edge) and the title carries a 1 dp text shadow, so
 * white stays legible on light photos (device-QA a11y note).
 */
@Composable
private fun HeroImage(
    recipe: ClippedRecipe,
    labels: RecipeKeeperLibraryLabels,
    height: Dp,
    topInset: Dp,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
) {
    val description = labels.heroImageDescription(recipe.title)
    Box(
        modifier = modifier
            .height(height)
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(recipe.pictures.first()))
                .crossfade(true)
                .build(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (topInset > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topInset + TopBarHeight)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = HERO_SCRIM_MIN_ALPHA),
                        1f to Color.Black.copy(alpha = HERO_SCRIM_MAX_ALPHA),
                    ),
                ),
        )
        Text(
            text = recipe.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.6f),
                    offset = Offset(0f, with(LocalDensity.current) { 1.dp.toPx() }),
                    blurRadius = with(LocalDensity.current) { 2.dp.toPx() },
                ),
            ),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .semantics { heading() },
        )
    }
}

/**
 * Servings / prep / cook / total chips, the "Possibly incomplete" badge and —
 * after an offline clip — the "Pictures unavailable" chip with its
 * "Retry pictures" action (WS11, UX spec §4).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaChips(
    recipe: ClippedRecipe,
    state: RecipeDetailState,
    labels: RecipeKeeperLibraryLabels,
    onIntent: (RecipeDetailIntent) -> Unit,
) {
    val chips = buildList {
        recipe.servings?.takeIf { it.isNotBlank() }?.let { raw ->
            val text = raw.trim().toIntOrNull()?.let(labels.servingsTemplate) ?: labels.servingsFreeform(raw.trim())
            add(Icons.Outlined.Group to text)
        }
        recipe.prepMinutes?.let { add(Icons.Outlined.Timer to labels.prepTemplate(it)) }
        recipe.cookMinutes?.let { add(Icons.Outlined.LocalFireDepartment to labels.cookTemplate(it)) }
        recipe.totalMinutes?.let { add(Icons.Outlined.Schedule to labels.totalTemplate(it)) }
    }
    if (chips.isEmpty() && !recipe.truncated && !recipe.picturesFailed) return
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { (icon, text) -> MetaChip(icon = icon, text = text) }
        if (recipe.truncated) {
            IncompleteBadge(text = labels.incompleteChip, modifier = Modifier.heightIn(min = 32.dp))
        }
        if (recipe.picturesFailed) {
            PicturesUnavailable(retrying = state.retryingPictures, labels = labels, onRetry = { onIntent(RecipeDetailIntent.RetryPictures) })
        }
    }
}

/** "Pictures unavailable" chip + "Retry pictures" button; both retry, the button shows the progress. */
@Composable
private fun PicturesUnavailable(
    retrying: Boolean,
    labels: RecipeKeeperLibraryLabels,
    onRetry: () -> Unit,
) {
    AssistChip(
        onClick = onRetry,
        enabled = !retrying,
        label = { Text(labels.picturesUnavailable) },
        leadingIcon = { Icon(Icons.Outlined.BrokenImage, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
    TextButton(onClick = onRetry, enabled = !retrying) {
        if (retrying) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(labels.retryPictures)
    }
}

/** Non-interactive outlined chip — M3 chips all expect a click, which TalkBack would announce. */
@Composable
private fun MetaChip(icon: ImageVector, text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SourceRow(
    recipe: ClippedRecipe,
    labels: RecipeKeeperLibraryLabels,
    onIntent: (RecipeDetailIntent) -> Unit,
) {
    val clippedOn = remember(recipe.clippedAt) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(recipe.clippedAt))
    }
    ListItem(
        headlineContent = { Text(recipe.sourceHost) },
        supportingContent = { Text(labels.clippedOn(clippedOn)) },
        leadingContent = { Icon(Icons.Outlined.Link, contentDescription = null) },
        trailingContent = {
            TextButton(onClick = { onIntent(RecipeDetailIntent.OpenOriginal) }) {
                Text(labels.openOriginal)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private fun LazyListScope.ingredientsSection(
    recipe: ClippedRecipe,
    labels: RecipeKeeperLibraryLabels,
    onIntent: (RecipeDetailIntent) -> Unit,
) {
    if (recipe.ingredients.isEmpty()) return
    val checked = recipe.checkedIngredients.count { it in recipe.ingredients.indices }
    item(key = "ingredients_header") {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
            SectionHeader(
                label = labels.ingredientsHeader,
                icon = Icons.Outlined.RestaurantMenu,
                trailing = {
                    if (checked > 0) {
                        TextButton(onClick = { onIntent(RecipeDetailIntent.ResetChecks) }) {
                            Text(labels.resetChecks)
                        }
                    }
                },
            )
            Text(
                text = labels.checkedCount(checked, recipe.ingredients.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 42.dp),
            )
        }
    }
    itemsIndexed(recipe.ingredients, key = { index, _ -> "ingredient_$index" }) { index, ingredient ->
        IngredientRow(
            text = ingredient,
            checked = index in recipe.checkedIngredients,
            onToggle = { onIntent(RecipeDetailIntent.ToggleIngredient(index)) },
        )
    }
}

@Composable
private fun IngredientRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (checked) TextDecoration.LineThrough else null,
        )
    }
}

private fun LazyListScope.stepsSection(
    recipe: ClippedRecipe,
    currentStep: Int?,
    labels: RecipeKeeperLibraryLabels,
    onIntent: (RecipeDetailIntent) -> Unit,
) {
    if (recipe.steps.isEmpty()) return
    item(key = "steps_header") {
        SectionHeader(
            label = labels.stepsHeader,
            icon = Icons.Outlined.LocalFireDepartment,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
    }
    itemsIndexed(recipe.steps, key = { index, _ -> "step_$index" }) { index, step ->
        StepRow(
            number = index + 1,
            total = recipe.steps.size,
            text = step,
            selected = currentStep == index,
            labels = labels,
            onClick = { onIntent(RecipeDetailIntent.SelectStep(index)) },
        )
    }
}

@Composable
private fun StepRow(
    number: Int,
    total: Int,
    text: String,
    selected: Boolean,
    labels: RecipeKeeperLibraryLabels,
    onClick: () -> Unit,
) {
    val description = "${labels.stepDescription(number, total)}. $text"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        NumberBadge(n = number, selected = selected)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(top = 2.dp),
        )
    }
}

private fun LazyListScope.picturesSection(recipe: ClippedRecipe, labels: RecipeKeeperLibraryLabels) {
    if (recipe.pictures.size < 2) return
    item(key = "pictures") {
        Column(modifier = Modifier.padding(top = 16.dp)) {
            SectionHeader(
                label = labels.picturesHeader,
                icon = Icons.Outlined.PhotoLibrary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PicturePager(pictures = recipe.pictures, labels = labels)
        }
    }
}

@Composable
private fun PicturePager(pictures: List<String>, labels: RecipeKeeperLibraryLabels) {
    val pagerState = rememberPagerState { pictures.size }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(pictures[page]))
                    .crossfade(true)
                    .build(),
                contentDescription = labels.pictureDescription(page + 1, pictures.size),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(pictures.size) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (active) 8.dp else 6.dp)
                        .background(
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

private fun LazyListScope.notesSection(recipe: ClippedRecipe, labels: RecipeKeeperLibraryLabels) {
    val notes = recipe.notes?.takeIf { it.isNotBlank() } ?: return
    item(key = "notes") {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(label = labels.notesHeader, icon = Icons.Outlined.Info)
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 42.dp),
            )
        }
    }
}
