package com.llmlocal.recipekeeper.recipes.presentation.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.llmlocal.core.designsystem.component.NumberBadge
import com.llmlocal.recipekeeper.recipes.domain.EditSection
import com.llmlocal.recipekeeper.recipes.domain.EditableLine
import com.llmlocal.recipekeeper.recipes.presentation.editor.RecipeEditorIntent

/** Lazy-list item key of one editor row; sections share a list, so the section prefixes the row key. */
internal fun lineItemKey(section: EditSection, key: Long): String = "${section.name}-$key"

/**
 * UI-only state of the line editors living in one `LazyColumn`: which row is
 * waiting for keyboard focus and the drag-to-reorder gesture in progress.
 *
 * Drag works on layout offsets: the dragged row keeps its identity, so after
 * each swap its translation is recomputed from where the lazy layout now puts
 * it, which keeps it glued to the finger while neighbours animate aside.
 */
@Stable
internal class LineEditorState {
    /** The list the rows live in; assigned by the screen on every composition. */
    var listState: LazyListState? = null

    /** Row that should take focus as soon as it is composed. */
    var pendingFocus by mutableStateOf<Pair<EditSection, Long>?>(null)

    var draggedKey by mutableStateOf<String?>(null)
        private set
    private var dragDelta by mutableFloatStateOf(0f)
    private var dragStartOffset = 0
    private var lastMoveOffset = Int.MIN_VALUE

    fun startDrag(itemKey: String) {
        val info = itemInfo(itemKey) ?: return
        draggedKey = itemKey
        dragDelta = 0f
        dragStartOffset = info.offset
        lastMoveOffset = Int.MIN_VALUE
    }

    /** Vertical translation that keeps the dragged row under the finger. */
    fun translationFor(itemKey: String): Float {
        if (draggedKey != itemKey) return 0f
        val current = itemInfo(itemKey)?.offset ?: return dragDelta
        return dragStartOffset + dragDelta - current
    }

    fun drag(dy: Float, section: EditSection, lines: List<EditableLine>, onMove: (from: Int, to: Int) -> Unit) {
        val dragged = draggedKey ?: return
        dragDelta += dy
        val info = itemInfo(dragged) ?: return
        // One swap per layout pass: wait until the dragged row has actually moved.
        if (info.offset == lastMoveOffset) return
        val centre = dragStartOffset + dragDelta + info.size / 2f
        val prefix = "${section.name}-"
        val target = listState?.layoutInfo?.visibleItemsInfo.orEmpty().firstOrNull {
            val k = it.key as? String
            k != null && k != dragged && k.startsWith(prefix) && centre >= it.offset && centre < it.offset + it.size
        } ?: return
        val from = lines.indexOfFirst { lineItemKey(section, it.key) == dragged }
        val to = lines.indexOfFirst { lineItemKey(section, it.key) == target.key }
        if (from < 0 || to < 0 || from == to) return
        lastMoveOffset = info.offset
        onMove(from, to)
    }

    fun endDrag() {
        draggedKey = null
        dragDelta = 0f
    }

    private fun itemInfo(itemKey: String) =
        listState?.layoutInfo?.visibleItemsInfo?.firstOrNull { it.key == itemKey }
}

/** Static copy for one section's rows; keeps [editableLineItems] argument lists short. */
internal class LineSectionCopy(
    val label: (position: Int) -> String,
    val remove: (position: Int) -> String,
    val reorder: (position: Int) -> String,
    val moveUp: String,
    val moveDown: String,
    val placeholder: String,
    val addLabel: String,
    val maxLines: Int,
)

/**
 * Rows of one reorderable section, then its "Add" button, as lazy items.
 * Ingredients use `ImeAction.Next` (Next on the last row adds a row); steps
 * keep Enter as a newline and show a number badge.
 */
internal fun LazyListScope.editableLineItems(
    section: EditSection,
    lines: List<EditableLine>,
    copy: LineSectionCopy,
    editor: LineEditorState,
    onIntent: (RecipeEditorIntent) -> Unit,
) {
    items(count = lines.size, key = { lineItemKey(section, lines[it].key) }) { index ->
        val line = lines[index]
        val itemKey = lineItemKey(section, line.key)
        val dragging = editor.draggedKey == itemKey
        EditableLineRow(
            section = section,
            line = line,
            index = index,
            lines = lines,
            copy = copy,
            editor = editor,
            dragging = dragging,
            onIntent = onIntent,
            modifier = if (dragging) Modifier else Modifier.animateItem(),
        )
    }
    item(key = "add-${section.name}") {
        OutlinedButton(
            onClick = { onIntent(RecipeEditorIntent.AddLine(section, lines.lastOrNull()?.key)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(copy.addLabel, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun EditableLineRow(
    section: EditSection,
    line: EditableLine,
    index: Int,
    lines: List<EditableLine>,
    copy: LineSectionCopy,
    editor: LineEditorState,
    dragging: Boolean,
    onIntent: (RecipeEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = index + 1
    val itemKey = lineItemKey(section, line.key)
    val requester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val isLast = index == lines.lastIndex
    val currentLines by rememberUpdatedState(lines)
    val currentIntent by rememberUpdatedState(onIntent)
    val density = LocalDensity.current

    LaunchedEffect(editor.pendingFocus) {
        if (editor.pendingFocus == section to line.key) {
            runCatching { requester.requestFocus() }
            editor.pendingFocus = null
        }
    }

    val lift = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = editor.translationFor(itemKey)
                if (dragging) {
                    scaleX = 1.02f
                    scaleY = 1.02f
                    shadowElevation = with(density) { 4.dp.toPx() }
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    clip = false
                }
            }
            .background(if (dragging) lift else androidx.compose.ui.graphics.Color.Transparent),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = copy.reorder(position)
                    customActions = buildList {
                        if (index > 0) {
                            add(CustomAccessibilityAction(copy.moveUp) {
                                onIntent(RecipeEditorIntent.MoveLine(section, index, index - 1))
                                true
                            })
                        }
                        if (!isLast) {
                            add(CustomAccessibilityAction(copy.moveDown) {
                                onIntent(RecipeEditorIntent.MoveLine(section, index, index + 1))
                                true
                            })
                        }
                    }
                }
                .pointerInput(section, line.key) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { editor.startDrag(itemKey) },
                        onDrag = { change, amount ->
                            change.consume()
                            editor.drag(amount.y, section, currentLines) { from, to ->
                                currentIntent(RecipeEditorIntent.MoveLine(section, from, to))
                            }
                        },
                        onDragEnd = { editor.endDrag() },
                        onDragCancel = { editor.endDrag() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (section == EditSection.STEPS) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) { NumberBadge(n = position) }
        }
        OutlinedTextField(
            value = line.text,
            onValueChange = { onIntent(RecipeEditorIntent.LineChanged(section, line.key, it)) },
            label = { Text(copy.label(position)) },
            placeholder = { Text(copy.placeholder) },
            maxLines = copy.maxLines,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (section == EditSection.INGREDIENTS) ImeAction.Next else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    if (isLast) {
                        onIntent(RecipeEditorIntent.AddLine(section, line.key))
                    } else {
                        focusManager.moveFocus(FocusDirection.Next)
                    }
                },
            ),
            modifier = Modifier.weight(1f).focusRequester(requester),
        )
        IconButton(onClick = { onIntent(RecipeEditorIntent.RemoveLine(section, line.key)) }) {
            Icon(Icons.Outlined.Close, contentDescription = copy.remove(position))
        }
    }
}
