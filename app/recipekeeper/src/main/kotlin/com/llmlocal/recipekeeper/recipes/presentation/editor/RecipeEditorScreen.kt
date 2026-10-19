package com.llmlocal.recipekeeper.recipes.presentation.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.AppErrorView
import com.llmlocal.core.designsystem.component.AppLoadingIndicator
import com.llmlocal.core.designsystem.component.SectionHeader
import com.llmlocal.recipekeeper.recipes.domain.EditSection
import com.llmlocal.recipekeeper.recipes.domain.EditorError
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import com.llmlocal.recipekeeper.recipes.presentation.editor.components.DiscardChangesDialog
import com.llmlocal.recipekeeper.recipes.presentation.editor.components.LineEditorState
import com.llmlocal.recipekeeper.recipes.presentation.editor.components.LineSectionCopy
import com.llmlocal.recipekeeper.recipes.presentation.editor.components.MetaFields
import com.llmlocal.recipekeeper.recipes.presentation.editor.components.PictureStrip
import com.llmlocal.recipekeeper.recipes.presentation.editor.components.SourceReadOnlyRow
import com.llmlocal.recipekeeper.recipes.presentation.editor.components.editableLineItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.Flow

private val TwoPaneMinWidth = 600.dp
private val SingleColumnMaxWidth = 720.dp
private val TwoPaneMaxWidth = 1280.dp
private val SideGutter = 16.dp

// Item positions inside the lists, used by `ScrollTo` effects (see the item order below).
private const val TITLE_INDEX = 1
private const val COMPACT_INGREDIENTS_INDEX = 4
private const val WIDE_INGREDIENTS_INDEX = 0

/**
 * The recipe editor (create and edit): pinned top bar with Save, then the
 * picture strip, fields, ingredient and step lists, notes and (for clipped
 * recipes) the read-only source. Single column up to 720dp, two panes from
 * 600dp wide so each list clears the keyboard on its own.
 *
 * Own `Scaffold(contentWindowInsets = safeDrawing)`, so the inner padding
 * already carries system bars and the IME and no `imePadding()` is needed.
 *
 * @param uiEffects focus / scroll commands from the ViewModel (forwarded by the route);
 *   buffered so a command sent before first composition is not lost
 * @param onAddPictures opens the photo picker (owned by the route)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(
    state: RecipeEditorState,
    labels: RecipeEditorLabels,
    snackbarHostState: SnackbarHostState,
    onIntent: (RecipeEditorIntent) -> Unit,
    onAddPictures: () -> Unit,
    uiEffects: Flow<RecipeEditorEffect>,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val compactState = rememberLazyListState()
    val leftState = rememberLazyListState()
    val rightState = rememberLazyListState()
    val lineEditor = remember { LineEditorState() }
    val titleFocus = remember { FocusRequester() }
    val canSave = state.edit != null && !state.isSaving && state.importingPictures == 0

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.mode == EditorMode.CREATE) labels.newTitle else labels.editTitle) },
                navigationIcon = {
                    IconButton(onClick = { onIntent(RecipeEditorIntent.RequestClose) }, enabled = !state.isSaving) {
                        Icon(Icons.Outlined.Close, contentDescription = labels.close)
                    }
                },
                actions = {
                    TextButton(onClick = { onIntent(RecipeEditorIntent.Save) }, enabled = canSave) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(labels.save, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val edit = state.edit
        when {
            state.isLoading -> AppLoadingIndicator(modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding))
            edit == null -> AppErrorView(
                message = labels.recipeNotFound,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
            )
            else -> BoxWithConstraints(Modifier.fillMaxSize().consumeWindowInsets(innerPadding)) {
                val wide = maxWidth >= TwoPaneMinWidth
                lineEditor.listState = if (wide) rightState else compactState
                val titleList = if (wide) leftState else compactState

                LaunchedEffect(uiEffects, wide) {
                    uiEffects.collect { effect ->
                        when (effect) {
                            RecipeEditorEffect.FocusTitle -> {
                                runCatching { titleList.scrollToItem(TITLE_INDEX) }
                                titleFocus.requestFocusWhenReady()
                            }
                            is RecipeEditorEffect.ScrollTo -> when (effect.target) {
                                EditorTarget.TITLE -> titleList.animateScrollToItem(TITLE_INDEX)
                                EditorTarget.INGREDIENTS ->
                                    if (wide) rightState.animateScrollToItem(WIDE_INGREDIENTS_INDEX)
                                    else compactState.animateScrollToItem(COMPACT_INGREDIENTS_INDEX)
                            }
                            is RecipeEditorEffect.FocusLine -> lineEditor.pendingFocus = effect.section to effect.key
                            else -> Unit
                        }
                    }
                }

                val body = EditorBody(state, edit, labels, onIntent, onAddPictures, titleFocus, lineEditor)
                if (wide) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Row(
                            modifier = Modifier.widthIn(max = TwoPaneMaxWidth).fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EditorColumn(leftState, innerPadding, Modifier.weight(1f)) {
                                body.topFields(this)
                                body.notesAndSource(this)
                            }
                            EditorColumn(rightState, innerPadding, Modifier.weight(1.1f)) {
                                body.lists(this)
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        EditorColumn(compactState, innerPadding, Modifier.widthIn(max = SingleColumnMaxWidth)) {
                            body.topFields(this)
                            body.lists(this)
                            body.notesAndSource(this)
                        }
                    }
                }
            }
        }
    }

    if (state.showDiscardDialog) {
        DiscardChangesDialog(
            mode = state.mode,
            labels = labels,
            onConfirm = { onIntent(RecipeEditorIntent.ConfirmDiscard) },
            onDismiss = { onIntent(RecipeEditorIntent.DismissDiscard) },
        )
    }
}

/** A scrolling list that clears system bars and the IME through [innerPadding] plus a 16dp gutter. */
@Composable
private fun EditorColumn(
    listState: LazyListState,
    innerPadding: PaddingValues,
    modifier: Modifier,
    content: LazyListScope.() -> Unit,
) {
    val direction = LocalLayoutDirection.current
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(direction) + SideGutter,
            end = innerPadding.calculateEndPadding(direction) + SideGutter,
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * The editor's lazy items split into three groups so compact (all three in
 * one list) and wide (left: top + notes; right: lists) share one definition.
 * Item order matters: [TITLE_INDEX] and [COMPACT_INGREDIENTS_INDEX] point into it.
 */
private class EditorBody(
    private val state: RecipeEditorState,
    private val edit: RecipeEdit,
    private val labels: RecipeEditorLabels,
    private val onIntent: (RecipeEditorIntent) -> Unit,
    private val onAddPictures: () -> Unit,
    private val titleFocus: FocusRequester,
    private val lineEditor: LineEditorState,
) {
    private val ingredientCopy = LineSectionCopy(
        label = labels.ingredientLabel,
        remove = labels.removeIngredient,
        reorder = labels.reorderIngredient,
        moveUp = labels.moveUp,
        moveDown = labels.moveDown,
        placeholder = labels.ingredientPlaceholder,
        addLabel = labels.addIngredient,
        maxLines = 3,
    )
    private val stepCopy = LineSectionCopy(
        label = labels.stepLabel,
        remove = labels.removeStep,
        reorder = labels.reorderStep,
        moveUp = labels.moveUp,
        moveDown = labels.moveDown,
        placeholder = labels.stepPlaceholder,
        addLabel = labels.addStep,
        maxLines = 8,
    )

    /** Items 0–3: pictures, title, description, meta. */
    fun topFields(scope: LazyListScope) = with(scope) {
        item(key = "pictures") {
            PictureStrip(
                pictures = edit.pictures,
                importing = state.importingPictures,
                maxPictures = state.maxPictures,
                labels = labels,
                onAdd = onAddPictures,
                onRemove = { onIntent(RecipeEditorIntent.RemovePicture(it)) },
                onSetCover = { onIntent(RecipeEditorIntent.SetCover(it)) },
            )
        }
        item(key = "title") {
            val error = EditorError.TitleRequired in state.errors
            OutlinedTextField(
                value = edit.title,
                onValueChange = { onIntent(RecipeEditorIntent.TitleChanged(it)) },
                label = { Text(labels.titleLabel) },
                isError = error,
                supportingText = if (error) ({ Text(labels.titleRequired) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
            )
        }
        item(key = "description") {
            OutlinedTextField(
                value = edit.description,
                onValueChange = { onIntent(RecipeEditorIntent.DescriptionChanged(it)) },
                label = { Text(labels.descriptionLabel) },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "meta") { MetaFields(edit = edit, labels = labels, onIntent = onIntent) }
    }

    /** Ingredients header + rows + add, then steps header + inline error + rows + add. */
    fun lists(scope: LazyListScope) = with(scope) {
        item(key = "ingredients-header") {
            SectionHeader(label = labels.ingredientsHeader, icon = Icons.Outlined.RestaurantMenu)
        }
        item(key = "ingredients-hint") { Hint(labels.ingredientsHint) }
        editableLineItems(EditSection.INGREDIENTS, edit.ingredients, ingredientCopy, lineEditor, onIntent)
        item(key = "steps-header") {
            SectionHeader(label = labels.stepsHeader, icon = Icons.Outlined.LocalFireDepartment)
        }
        item(key = "steps-error") {
            AnimatedVisibility(
                visible = EditorError.NeedContent in state.errors,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Text(
                    text = labels.needContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item(key = "steps-hint") { Hint(labels.stepsHint) }
        editableLineItems(EditSection.STEPS, edit.steps, stepCopy, lineEditor, onIntent)
    }

    fun notesAndSource(scope: LazyListScope) = with(scope) {
        item(key = "notes") {
            OutlinedTextField(
                value = edit.notes,
                onValueChange = { onIntent(RecipeEditorIntent.NotesChanged(it)) },
                label = { Text(labels.notesLabel) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (edit.origin == RecipeOrigin.CLIPPED && edit.sourceUrl.isNotBlank()) {
            item(key = "source") {
                SourceReadOnlyRow(host = edit.sourceHost, url = edit.sourceUrl, labels = labels)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Requests focus once the field is attached: right after the editor opens the
 * title is not composed yet, and `requestFocus` throws until it is.
 */
private suspend fun FocusRequester.requestFocusWhenReady(attempts: Int = 10) {
    repeat(attempts) {
        try {
            requestFocus()
            return
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            awaitFrame()
        }
    }
}
