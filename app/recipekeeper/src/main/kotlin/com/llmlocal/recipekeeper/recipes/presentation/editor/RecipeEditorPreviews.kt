package com.llmlocal.recipekeeper.recipes.presentation.editor

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.recipekeeper.designsystem.RecipeKeeperTokens
import com.llmlocal.recipekeeper.recipes.domain.EditableLine
import com.llmlocal.recipekeeper.recipes.domain.EditorError
import com.llmlocal.recipekeeper.recipes.domain.EditorPicture
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import kotlinx.coroutines.flow.emptyFlow

/** `@Preview`s of the editor: create (empty), edit (filled), errors, importing picture, wide 840dp, dark. */
@Composable
private fun EditorPreviewSurface(state: RecipeEditorState, darkTheme: Boolean = false) {
    AppTheme(tokens = RecipeKeeperTokens, darkTheme = darkTheme) {
        Surface {
            RecipeEditorScreen(
                state = state,
                labels = rememberRecipeEditorLabels(),
                snackbarHostState = remember { SnackbarHostState() },
                onIntent = {},
                onAddPictures = {},
                uiEffects = emptyFlow(),
            )
        }
    }
}

private val blankDraft = RecipeEdit(
    id = null,
    origin = RecipeOrigin.CREATED,
    ingredients = listOf(EditableLine(0, "")),
    steps = listOf(EditableLine(1, "")),
)

private val filledDraft = RecipeEdit(
    id = "r1",
    origin = RecipeOrigin.CLIPPED,
    title = "Roasted tomato and white bean soup",
    description = "A weeknight soup that tastes like it simmered all afternoon.",
    servings = "4",
    prepMinutes = 15,
    cookMinutes = 40,
    ingredients = listOf(
        EditableLine(0, "1 kg ripe tomatoes, halved", 0),
        EditableLine(1, "2 tbsp olive oil", 1),
        EditableLine(2, "400 g tin cannellini beans, drained", 2),
    ),
    steps = listOf(
        EditableLine(0, "Roast the tomatoes with the oil for 30 minutes until blistered.", 0),
        EditableLine(1, "Blend with the stock, stir in the beans and season to taste.", 1),
    ),
    notes = "Freezes well for up to three months.",
    pictures = listOf(
        EditorPicture(1, "/preview/001.jpg", isNew = false),
        EditorPicture(2, "/preview/002.jpg", isNew = true),
    ),
    sourceUrl = "https://example.com/recipes/roasted-tomato-soup",
    sourceHost = "example.com",
)

private fun editing(edit: RecipeEdit, initial: RecipeEdit? = edit, mode: EditorMode = EditorMode.EDIT) =
    RecipeEditorState(mode = mode, edit = edit, initial = initial, isLoading = false)

@Preview(name = "Editor · create (empty)", showBackground = true, heightDp = 800)
@Composable
private fun EditorCreatePreview() = EditorPreviewSurface(editing(blankDraft, mode = EditorMode.CREATE))

@Preview(name = "Editor · edit (filled)", showBackground = true, heightDp = 1100)
@Composable
private fun EditorEditPreview() = EditorPreviewSurface(editing(filledDraft))

@Preview(name = "Editor · validation errors", showBackground = true, heightDp = 800)
@Composable
private fun EditorErrorsPreview() = EditorPreviewSurface(
    editing(blankDraft, mode = EditorMode.CREATE).copy(
        errors = setOf(EditorError.TitleRequired, EditorError.NeedContent),
    ),
)

@Preview(name = "Editor · importing a picture", showBackground = true, heightDp = 800)
@Composable
private fun EditorImportingPreview() = EditorPreviewSurface(editing(filledDraft).copy(importingPictures = 2))

@Preview(name = "Editor · discard dialog", showBackground = true, heightDp = 800)
@Composable
private fun EditorDiscardPreview() = EditorPreviewSurface(
    editing(filledDraft.copy(title = "Edited title"), initial = filledDraft).copy(showDiscardDialog = true),
)

@Preview(name = "Editor · wide 840dp", showBackground = true, widthDp = 840, heightDp = 720)
@Composable
private fun EditorWidePreview() = EditorPreviewSurface(editing(filledDraft))

@Preview(
    name = "Editor · dark",
    showBackground = true,
    heightDp = 1100,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun EditorDarkPreview() = EditorPreviewSurface(editing(filledDraft), darkTheme = true)

@Preview(name = "Editor · not found", showBackground = true, heightDp = 400)
@Composable
private fun EditorNotFoundPreview() = EditorPreviewSurface(
    RecipeEditorState(mode = EditorMode.EDIT, isLoading = false),
)
