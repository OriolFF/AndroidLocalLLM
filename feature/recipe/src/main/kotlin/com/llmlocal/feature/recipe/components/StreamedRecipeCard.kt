package com.llmlocal.feature.recipe.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llmlocal.core.model.Recipe

/**
 * Card showing the generated recipe. While the model is streaming, the raw
 * cumulative text is shown with an animated primary-tinted cursor. Once a
 * structured [Recipe] is available, it is rendered with section icons
 * (ingredients / steps / notes), animated numbered steps, and better
 * typography.
 *
 * The streaming → structured transition crossfades via [AnimatedContent].
 */
@Composable
fun StreamedRecipeCard(
    streamedText: String,
    recipe: Recipe?,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        AnimatedContent(
            targetState = recipe,
            transitionSpec = {
                if (targetState == null) {
                    fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 200))
                } else {
                    fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 200))
                }
            },
            label = "recipeContent",
        ) { currentRecipe ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (currentRecipe != null) {
                    StructuredRecipe(currentRecipe)
                } else {
                    StreamingText(text = streamedText, isGenerating = isGenerating)
                }
            }
        }
    }
}

@Composable
private fun StructuredRecipe(recipe: Recipe) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Title
        Text(
            text = recipe.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Ingredients
        if (recipe.ingredients.isNotEmpty()) {
            SectionHeader(
                label = "Ingredients",
                icon = Icons.Outlined.RestaurantMenu,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recipe.ingredients.forEach { ingredient ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Bullet()
                        Text(
                            text = ingredient,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Steps
        if (recipe.steps.isNotEmpty()) {
            SectionHeader(
                label = "Steps",
                icon = Icons.Outlined.LocalFireDepartment,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                recipe.steps.forEachIndexed { index, step ->
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInHorizontally(
                            initialOffsetX = { it / 4 },
                            animationSpec = tween(
                                durationMillis = 280,
                                delayMillis = index * 50,
                            ),
                        ) + fadeIn(
                            animationSpec = tween(
                                durationMillis = 280,
                                delayMillis = index * 50,
                            ),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            NumberBadge(number = index + 1)
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // Notes
        val notes = recipe.notes
        if (!notes.isNullOrBlank()) {
            SectionHeader(
                label = "Notes",
                icon = Icons.Outlined.Info,
            )
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(label: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NumberBadge(number: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun Bullet() {
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ),
    )
}

@Composable
private fun StreamingText(text: String, isGenerating: Boolean) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )
    val textAlpha by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "textPulse",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (text.isNotBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isGenerating) textAlpha else 1f),
            )
        } else if (isGenerating) {
            // Placeholder row when generation starts but the first token
            // hasn't arrived yet.
            Text(
                text = "Warming up the model…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isGenerating) {
            // Custom animated caret — a 24dp wide primary-tinted bar with a
            // blinking alpha, replacing the legacy unicode "▍" placeholder
            // so the cursor animates nicely across all font fallbacks.
            Box(
                modifier = Modifier
                    .width(if (isGenerating) 24.dp else 0.dp)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(vertical = 1.dp)
                    .alpha(cursorAlpha),
            )
        }
    }
}
