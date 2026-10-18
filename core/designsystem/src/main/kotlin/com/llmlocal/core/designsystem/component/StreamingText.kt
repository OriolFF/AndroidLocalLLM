package com.llmlocal.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * Raw LLM output as it streams in, for any output card.
 *
 * Three states:
 *  - no [text] yet and [isGenerating] — shows [warmingUpLabel] in a muted
 *    colour plus the [StreamingCursor], so the model's first-token latency
 *    reads as "thinking", not as a frozen screen
 *  - [text] and [isGenerating] — the cumulative text, gently pulsing, with
 *    the cursor underneath
 *  - not generating — the final text at full opacity, no cursor
 *
 * Brand-free: `onSurface` / `onSurfaceVariant` for text, `primary` for the
 * cursor. The warming-up copy is the caller's (`LlmTaskLabels.warmingUpLabel`
 * in the shipping apps).
 *
 * @param text cumulative raw text received so far (`state.streamedText`).
 * @param isGenerating whether a generation pass is still in flight.
 * @param warmingUpLabel placeholder shown while [text] is still empty.
 */
@Composable
fun StreamingText(
    text: String,
    isGenerating: Boolean,
    warmingUpLabel: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "streaming-text")
    val textAlpha by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "streaming-text-pulse",
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
            Text(
                text = warmingUpLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isGenerating) {
            StreamingCursor()
        }
    }
}

/**
 * Blinking 24×2dp primary-coloured bar that marks "more is coming". Owns
 * its own blink so it can be dropped anywhere — under a [StreamingText],
 * after the unparsed tail of an incremental draft, next to a skeleton.
 */
@Composable
fun StreamingCursor(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "streaming-cursor")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "streaming-cursor-alpha",
    )
    Box(
        modifier = modifier
            .width(24.dp)
            .height(2.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary),
    )
}
