package com.llmlocal.recipekeeper.recipes.presentation.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit
import com.llmlocal.recipekeeper.recipes.presentation.editor.RecipeEditorIntent
import com.llmlocal.recipekeeper.recipes.presentation.editor.RecipeEditorLabels

private val FourAcrossMinWidth = 480.dp

/** Servings (free text) + prep / cook / total minutes: 2×2 at phone width, one row when wider. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MetaFields(
    edit: RecipeEdit,
    labels: RecipeEditorLabels,
    onIntent: (RecipeEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= FourAcrossMinWidth) 4 else 2
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = perRow,
        ) {
            val fieldModifier = Modifier.weight(1f)
            OutlinedTextField(
                value = edit.servings,
                onValueChange = { onIntent(RecipeEditorIntent.ServingsChanged(it)) },
                label = { Text(labels.servingsLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = fieldModifier,
            )
            MinutesField(edit.prepMinutes, labels.prepLabel, null, { onIntent(RecipeEditorIntent.PrepChanged(it)) }, fieldModifier)
            MinutesField(edit.cookMinutes, labels.cookLabel, null, { onIntent(RecipeEditorIntent.CookChanged(it)) }, fieldModifier)
            val derived = if (edit.totalMinutes == null && edit.prepMinutes != null && edit.cookMinutes != null) {
                labels.totalDerived(edit.prepMinutes + edit.cookMinutes)
            } else {
                null
            }
            MinutesField(edit.totalMinutes, labels.totalLabel, derived, { onIntent(RecipeEditorIntent.TotalChanged(it)) }, fieldModifier)
        }
    }
}

@Composable
private fun MinutesField(
    minutes: Int?,
    label: String,
    placeholder: String?,
    onChange: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = minutes?.toString().orEmpty(),
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}
