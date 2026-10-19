package com.llmlocal.feature.llmtask.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Generic input row for the LLM-task scaffold. Lets the user type a free-text
 * entry and submit it (either by tapping "Add" or pressing the IME action).
 *
 * Visual: a softly rounded outlined field paired with a circular
 * `FilledIconButton` for the Add action. Both share the `large` shape so the
 * row reads as a single visual unit.
 *
 * The `enabled` flag controls both the text field and the Add button.
 *
 * @param value the current draft text
 * @param onValueChange called when the user types
 * @param onAdd called when the user submits (tap or IME done)
 * @param labels UI copy for the field label, placeholder, and Add button
 *   content descriptions.
 */
@Composable
fun InputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    labels: InputRowLabels,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(labels.fieldLabel) },
            placeholder = { Text(labels.fieldPlaceholder) },
            singleLine = true,
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        FilledIconButton(
            onClick = onAdd,
            enabled = enabled && value.isNotBlank(),
            shape = MaterialTheme.shapes.large,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                )
                .semantics {
                    contentDescription = if (enabled) labels.addActionDescription else labels.addDisabledActionDescription
                },
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
            )
        }
    }
}

/**
 * Copy for [InputRow]. Carved out of [LlmTaskLabels] so the chip-row
 * composable can be passed just what it needs.
 */
data class InputRowLabels(
    val fieldLabel: String,
    val fieldPlaceholder: String,
    val addActionDescription: String,
    val addDisabledActionDescription: String,
)