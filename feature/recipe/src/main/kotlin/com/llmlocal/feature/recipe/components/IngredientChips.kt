package com.llmlocal.feature.recipe.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.core.model.Ingredient

/**
 * Renders the current list of ingredients as a wrap-row of chips. Tapping
 * the close icon on a chip removes that ingredient.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IngredientChips(
    ingredients: List<Ingredient>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ingredients.isEmpty()) return
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ingredients.forEach { ingredient ->
            AssistChip(
                onClick = { onRemove(ingredient.name) },
                label = { Text(ingredient.name) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove ${ingredient.name}",
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}
