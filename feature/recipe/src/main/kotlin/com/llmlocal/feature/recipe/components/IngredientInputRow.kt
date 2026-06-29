package com.llmlocal.feature.recipe.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Restaurant
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
 * Input row used at the top of the recipe screen. Lets the user type an
 * ingredient name and submit it (either by tapping "Add" or pressing the
 * IME action).
 *
 * Visual: a softly rounded outlined field with a leading restaurant icon,
 * paired with a circular `FilledIconButton` for the Add action. Both share
 * the `large` shape so the row reads as a single visual unit.
 *
 * The `enabled` flag controls both the text field and the Add button. When
 * false (no model installed) the Add button is greyed out and a hidden
 * contentDescription says so.
 */
@Composable
fun IngredientInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
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
            label = { Text("Ingredient") },
            placeholder = { Text("e.g. eggs, garlic, tomatoes…") },
            singleLine = true,
            enabled = enabled,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Restaurant,
                    contentDescription = null,
                )
            },
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
                    contentDescription = if (enabled) "Add ingredient" else "Add ingredient, disabled"
                },
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
            )
        }
    }
}
