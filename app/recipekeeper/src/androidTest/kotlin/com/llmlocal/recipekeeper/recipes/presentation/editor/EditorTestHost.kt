package com.llmlocal.recipekeeper.recipes.presentation.editor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.test.platform.app.InstrumentationRegistry
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.recipekeeper.designsystem.RecipeKeeperTokens
import com.llmlocal.recipekeeper.recipes.domain.EditSection
import com.llmlocal.recipekeeper.recipes.domain.EditableLine
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import com.llmlocal.recipekeeper.recipes.domain.usecase.SplitPastedLinesUseCase
import kotlinx.coroutines.flow.emptyFlow

/** Draft with [ingredients] ingredient and [steps] step rows. */
internal fun draft(ingredients: Int = 3, steps: Int = 3) = RecipeEdit(
    id = "r1",
    origin = RecipeOrigin.CREATED,
    title = "Soup",
    ingredients = List(ingredients) { EditableLine(it.toLong(), "Item $it", it) },
    steps = List(steps) { EditableLine(1000L + it, "Do thing $it", it) },
)

/**
 * The real [RecipeEditorScreen] behind a minimal in-memory reducer (add, move,
 * edit line) so tests drive the same intents the ViewModel receives.
 * [intents] sees every intent after it has been applied.
 */
@Composable
internal fun EditorTestHost(
    initial: RecipeEdit,
    modifier: Modifier = Modifier,
    intents: (RecipeEditorIntent) -> Unit = {},
) {
    var edit by remember { mutableStateOf(initial) }
    var nextKey by remember { mutableStateOf(10_000L) }
    val state = RecipeEditorState(mode = EditorMode.EDIT, edit = edit, initial = initial, isLoading = false)
    AppTheme(tokens = RecipeKeeperTokens) {
        Surface {
            RecipeEditorScreen(
                state = state,
                labels = rememberRecipeEditorLabels(),
                snackbarHostState = remember { SnackbarHostState() },
                onIntent = { intent ->
                    edit = edit.reduce(intent) { nextKey++ }
                    intents(intent)
                },
                onAddPictures = {},
                uiEffects = emptyFlow(),
                modifier = modifier,
            )
        }
    }
}

private fun RecipeEdit.reduce(intent: RecipeEditorIntent, newKey: () -> Long): RecipeEdit = when (intent) {
    is RecipeEditorIntent.AddLine -> mapLines(intent.section) { lines ->
        val at = lines.indexOfFirst { it.key == intent.afterKey }.let { if (it < 0) lines.size else it + 1 }
        lines.toMutableList().also { it.add(at, EditableLine(newKey(), "")) }
    }
    is RecipeEditorIntent.MoveLine -> mapLines(intent.section) { lines ->
        lines.toMutableList().also { it.add(intent.to, it.removeAt(intent.from)) }
    }
    is RecipeEditorIntent.LineChanged -> mapLines(intent.section) { lines ->
        // Mirrors RecipeEditorViewModel.changeLine: a value with a newline is a paste and becomes rows.
        val parts = if ('\n' in intent.text) SplitPastedLinesUseCase()(intent.text, intent.section) else listOf(intent.text)
        lines.flatMap { line ->
            if (line.key != intent.key) listOf(line)
            else listOf(line.copy(text = parts.firstOrNull().orEmpty())) + parts.drop(1).map { EditableLine(newKey(), it) }
        }
    }
    else -> this
}

private fun RecipeEdit.mapLines(section: EditSection, f: (List<EditableLine>) -> List<EditableLine>) = when (section) {
    EditSection.INGREDIENTS -> copy(ingredients = f(ingredients))
    EditSection.STEPS -> copy(steps = f(steps))
}

/** Invokes the custom accessibility action with [label] the way TalkBack's actions menu does. */
internal fun SemanticsNodeInteraction.performCustomAccessibilityActionWithLabel(label: String): SemanticsNodeInteraction {
    val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
        ?.firstOrNull { it.label == label }
        ?: throw AssertionError("No custom accessibility action \"$label\" on the node")
    // The custom action is a plain lambda on the node; run it on the main thread like an accessibility service would.
    InstrumentationRegistry.getInstrumentation().runOnMainSync { action.action() }
    return this
}
