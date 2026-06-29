package com.llmlocal.feature.recipe.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.llmlocal.core.model.Ingredient

/**
 * Renders the current list of ingredients as a wrap-row of chips with a
 * staggered fade+scale-in animation. Tapping the close icon on a chip
 * removes that ingredient (the chip itself plays the fade-out animation).
 *
 * The anim is done per-chip via `AnimatedVisibility` + a brief delay so
 * the chips appear left-to-right rather than all at once. Doesn't animate
 * on every recomposition — only when the visible set actually changes.
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
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ingredients.forEachIndexed { index, ingredient ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 220,
                        delayMillis = index * 35,
                    ),
                ) + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = tween(
                        durationMillis = 220,
                        delayMillis = index * 35,
                    ),
                ),
                exit = fadeOut(animationSpec = tween(durationMillis = 160)),
            ) {
                IngredientChip(ingredient = ingredient, onRemove = onRemove)
            }
        }
    }
}

@Composable
private fun IngredientChip(
    ingredient: Ingredient,
    onRemove: (String) -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 200),
        label = "chipAlpha",
    )
    InputChip(
        selected = false,
        onClick = { onRemove(ingredient.name) },
        label = {
            Text(
                text = ingredient.name,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Remove ${ingredient.name}",
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
        shape = MaterialTheme.shapes.small,
        colors = InputChipDefaults.inputChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
            trailingIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier.alpha(alpha),
    )
}
