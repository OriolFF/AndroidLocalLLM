package com.llmlocal.feature.recipe.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.core.model.Recipe

/**
 * Card showing the generated recipe. While the model is streaming, the raw
 * cumulative text is shown. Once a structured [Recipe] is available, it
 * is rendered with proper sections.
 */
@Composable
fun StreamedRecipeCard(
    streamedText: String,
    recipe: Recipe?,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (recipe != null) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (recipe.ingredients.isNotEmpty()) {
                    Text(
                        text = "Ingredients",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    recipe.ingredients.forEach { ingredient ->
                        Text(text = "• $ingredient", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (recipe.steps.isNotEmpty()) {
                    Text(
                        text = "Steps",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    recipe.steps.forEachIndexed { index, step ->
                        Text(
                            text = "${index + 1}. $step",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                val notes = recipe.notes
                if (!notes.isNullOrBlank()) {
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(text = notes, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                StreamingText(
                    text = streamedText,
                    isGenerating = isGenerating,
                )
            }
        }
    }
}

@Composable
private fun StreamingText(text: String, isGenerating: Boolean) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )
    Text(
        text = text + if (isGenerating) "▍" else "",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isGenerating) alpha else 1f),
        modifier = Modifier.padding(PaddingValues(0.dp)),
    )
}
