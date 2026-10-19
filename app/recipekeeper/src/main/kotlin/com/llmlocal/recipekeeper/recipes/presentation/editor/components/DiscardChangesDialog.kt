package com.llmlocal.recipekeeper.recipes.presentation.editor.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.llmlocal.recipekeeper.recipes.presentation.editor.EditorMode
import com.llmlocal.recipekeeper.recipes.presentation.editor.RecipeEditorLabels

/** "Discard changes?" confirmation shown when closing a dirty editor. */
@Composable
internal fun DiscardChangesDialog(
    mode: EditorMode,
    labels: RecipeEditorLabels,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(labels.discardTitle) },
        text = {
            Text(if (mode == EditorMode.CREATE) labels.discardMessageNew else labels.discardMessageEdit)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(labels.discard, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(labels.keepEditing) }
        },
    )
}
