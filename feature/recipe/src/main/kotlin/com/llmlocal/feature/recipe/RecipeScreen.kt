package com.llmlocal.feature.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.feature.recipe.components.IngredientChips
import com.llmlocal.feature.recipe.components.IngredientInputRow
import com.llmlocal.feature.recipe.components.NoModelAvailableBanner
import com.llmlocal.feature.recipe.components.StreamedRecipeCard
import com.llmlocal.feature.recipe.mvi.ModelStatus
import com.llmlocal.feature.recipe.mvi.RecipeIntent
import com.llmlocal.feature.recipe.mvi.RecipeState

/**
 * Stateless recipe-generation screen. The composable receives a
 * [RecipeState] and an [onIntent] callback; it does not know about the
 * ViewModel. This makes it previewable and easy to test.
 */
@Composable
fun RecipeScreen(
    state: RecipeState,
    onIntent: (RecipeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---------------------------------------------------------------
        // "No model available" banner. Shown when the user has no model
        // selected, or when the selected model's file is missing on disk.
        // Tapping the CTA routes the user to the model management screen.
        // ---------------------------------------------------------------
        if (state.modelStatus is ModelStatus.NoModelAvailable) {
            NoModelAvailableBanner(
                selectedModel = state.selectedModel,
                onOpenModels = { onIntent(RecipeIntent.OpenModelManagement) },
            )
        }

        IngredientInputRow(
            value = state.input,
            onValueChange = { onIntent(RecipeIntent.UpdateInput(it)) },
            onAdd = { onIntent(RecipeIntent.AddIngredient) },
            enabled = state.modelStatus is ModelStatus.Ready,
        )

        IngredientChips(
            ingredients = state.ingredients,
            onRemove = { onIntent(RecipeIntent.RemoveIngredient(it)) },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onIntent(RecipeIntent.GenerateRecipe) },
                enabled = state.canGenerate,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text(
                    text = if (state.isGenerating) "Generating…" else "Generate recipe",
                )
            }
            if (state.isGenerating) {
                OutlinedButton(
                    onClick = { onIntent(RecipeIntent.CancelGeneration) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Cancel")
                }
            }
        }

        if (state.recipe != null || state.streamedText.isNotEmpty() || state.isGenerating) {
            StreamedRecipeCard(
                streamedText = state.streamedText,
                recipe = state.recipe,
                isGenerating = state.isGenerating,
            )
        }

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}