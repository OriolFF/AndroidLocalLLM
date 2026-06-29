package com.llmlocal.feature.recipe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.EmptyState
import com.llmlocal.core.designsystem.component.GradientHero
import com.llmlocal.feature.recipe.components.IngredientChips
import com.llmlocal.feature.recipe.components.IngredientInputRow
import com.llmlocal.feature.recipe.components.NoModelAvailableBanner
import com.llmlocal.feature.recipe.components.StreamedRecipeCard
import com.llmlocal.feature.recipe.mvi.ModelStatus
import com.llmlocal.feature.recipe.mvi.RecipeIntent
import com.llmlocal.feature.recipe.mvi.RecipeState

/**
 * Stateless recipe-generation screen. Receives a [RecipeState] and an
 * [onIntent] callback; it does not know about the ViewModel. Previewable.
 *
 * Layout (top to bottom inside a [LazyColumn]):
 *
 *  1. **No-model banner** — only when [ModelStatus.NoModelAvailable].
 *  2. **Hero** — "What's in your kitchen?" with subtitle.
 *  3. **Ingredients card** — `Surface` containing the input row, an empty
 *     state when no ingredients are added, and the chip row.
 *  4. **Generate CTA** — full-width [Button] that morphs into an outlined
 *     Stop button while generating.
 *  5. **Streamed recipe card** — visible when streaming / structured.
 *  6. **Error** — replaced by an [EmptyState] instead of a plain red text.
 */
@Composable
fun RecipeScreen(
    state: RecipeState,
    onIntent: (RecipeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 0.dp,
            end = 0.dp,
            top = 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---- 1. No-model banner (conditional) ---------------------------
        if (state.modelStatus is ModelStatus.NoModelAvailable) {
            item(key = "no_model") {
                NoModelAvailableBanner(
                    selectedModel = state.selectedModel,
                    onOpenModels = { onIntent(RecipeIntent.OpenModelManagement) },
                )
            }
        }

        // ---- 2. Hero --------------------------------------------------
        item(key = "hero") {
            GradientHero(
                title = "What's in your kitchen?",
                subtitle = "Add a few ingredients and we'll cook up a recipe — " +
                    "all on your device.",
                icon = Icons.Filled.RestaurantMenu,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // ---- 3. Ingredients card --------------------------------------
        item(key = "ingredients_card") {
            IngredientsCard(state = state, onIntent = onIntent)
        }

        // ---- 4. Generate CTA -------------------------------------------
        item(key = "generate_cta") {
            GenerateCta(
                isGenerating = state.isGenerating,
                canGenerate = state.canGenerate,
                onGenerate = { onIntent(RecipeIntent.GenerateRecipe) },
                onCancel = { onIntent(RecipeIntent.CancelGeneration) },
            )
        }

        // ---- 5. Recipe output (conditional) ---------------------------
        if (state.recipe != null || state.streamedText.isNotEmpty() || state.isGenerating) {
            item(key = "output_card") {
                StreamedRecipeCard(
                    streamedText = state.streamedText,
                    recipe = state.recipe,
                    isGenerating = state.isGenerating,
                )
            }
        }

        // ---- 6. Error (conditional) -----------------------------------
        if (state.errorMessage != null) {
            item(key = "error") {
                EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = "Something went wrong",
                    message = state.errorMessage,
                    actionLabel = "Dismiss",
                    onAction = { onIntent(RecipeIntent.DismissError) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Card wrapping the ingredient input + chip list, with an inline empty
 * state when no ingredients are added yet.
 */
@Composable
private fun IngredientsCard(
    state: RecipeState,
    onIntent: (RecipeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val elevatedShape = MaterialTheme.shapes.large
    val padding by animateDpAsState(
        targetValue = if (state.ingredients.isEmpty()) 20.dp else 12.dp,
        animationSpec = tween(durationMillis = 200),
        label = "ingredientsCardPadding",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = elevatedShape,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = "Ingredients",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            IngredientInputRow(
                value = state.input,
                onValueChange = { onIntent(RecipeIntent.UpdateInput(it)) },
                onAdd = { onIntent(RecipeIntent.AddIngredient) },
                enabled = state.modelStatus is ModelStatus.Ready,
            )

            AnimatedVisibility(
                visible = state.ingredients.isEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Your kitchen is empty",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Add tomatoes, garlic, eggs… to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            IngredientChips(
                ingredients = state.ingredients,
                onRemove = { onIntent(RecipeIntent.RemoveIngredient(it)) },
            )
        }
    }
}

/**
 * Generate / Stop button block. When `isGenerating`, the primary button
 * morphs into a `LinearProgressIndicator` and the outlined "Stop" CTA
 * appears below it — keeps the CTA visible without forcing a layout jump.
 */
@Composable
private fun GenerateCta(
    isGenerating: Boolean,
    canGenerate: Boolean,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isGenerating) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(
                    imageVector = Icons.Outlined.StopCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Stop generating",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        } else {
            Button(
                onClick = onGenerate,
                enabled = canGenerate,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Generate recipe",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}
