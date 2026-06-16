package com.llmlocal.feature.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.llmlocal.feature.recipe.components.IngredientChips
import com.llmlocal.feature.recipe.components.IngredientInputRow
import com.llmlocal.feature.recipe.components.ModelDownloadBanner
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
        // Demo mode toggle — always visible at the top so the user can
        // test the full recipe flow without waiting for a 2+ GB download.
        // ---------------------------------------------------------------
        DemoModeRow(
            useDemoEngine = state.useDemoEngine,
            onToggle = { onIntent(RecipeIntent.SetUseDemoEngine(it)) },
        )

        // ---------------------------------------------------------------
        // Model status banner. Hidden once the real model is Ready. In
        // Demo mode we show a short "using sample data" hint instead of
        // the full download banner.
        // ---------------------------------------------------------------
        when {
            state.useDemoEngine -> DemoModeHint()
            state.modelStatus !is ModelStatus.Ready -> ModelDownloadBanner(
                status = state.modelStatus,
                modelSizeText = state.modelHumanSize,
                modelFilename = state.modelFilename,
                onDownload = { onIntent(RecipeIntent.RetryModelDownload) },
                onCancel = { onIntent(RecipeIntent.CancelModelDownload) },
            )
        }

        IngredientInputRow(
            value = state.input,
            onValueChange = { onIntent(RecipeIntent.UpdateInput(it)) },
            onAdd = { onIntent(RecipeIntent.AddIngredient) },
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
                    text = when {
                        state.isGenerating -> "Generating…"
                        state.useDemoEngine -> "Generate sample recipe"
                        else -> "Generate recipe"
                    }
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

@Composable
private fun DemoModeRow(useDemoEngine: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { role = Role.Switch },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(text = "Demo mode", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (useDemoEngine) {
                    "Using a canned recipe — no model download required."
                } else {
                    "Off — the real on-device LLM will be used."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = useDemoEngine,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun DemoModeHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = "Demo mode", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "The on-device LLM is not loaded. A canned sample recipe will be " +
                "streamed so you can preview the flow. Toggle off to start the download.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
