package com.llmlocal.recipekeeper.recipes.presentation.editor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.recipekeeper.designsystem.RecipeKeeperTokens
import com.llmlocal.recipekeeper.recipes.domain.EditSection
import com.llmlocal.recipekeeper.recipes.domain.EditableLine
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
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
        lines.map { if (it.key == intent.key) it.copy(text = intent.text) else it }
    }
    else -> this
}

private fun RecipeEdit.mapLines(section: EditSection, f: (List<EditableLine>) -> List<EditableLine>) = when (section) {
    EditSection.INGREDIENTS -> copy(ingredients = f(ingredients))
    EditSection.STEPS -> copy(steps = f(steps))
}
