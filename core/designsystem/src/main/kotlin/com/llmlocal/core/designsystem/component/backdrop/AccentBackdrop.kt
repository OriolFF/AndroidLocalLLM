package com.llmlocal.core.designsystem.component.backdrop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.color.ImageAccent
import com.llmlocal.core.designsystem.color.washed

/**
 * Full-bleed, static background that colours a screen with the picture
 * shown on it.
 *
 * Place it as the first child of the screen's root `Box` and make the
 * `Scaffold` above it transparent. It always paints the theme surface first
 * and cross-fades once to the chosen [style] when [accent] arrives; after
 * that nothing animates. Dark theme is detected from the surface luminance,
 * so the same code tints toward near-black at night.
 */
@Composable
fun AccentBackdrop(
    accent: ImageAccent?,
    style: AccentBackdropStyle,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    val target = if (style == AccentBackdropStyle.NONE) null else accent
    Box(modifier = modifier.fillMaxSize().drawBehind { drawRect(surface) }) {
        Crossfade(targetState = target, animationSpec = tween(ACCENT_FADE_MILLIS), label = "accentBackdrop") { current ->
            if (current != null) {
                val dark = surface.luminance() < 0.5f
                when (style) {
                    AccentBackdropStyle.NONE -> Unit
                    AccentBackdropStyle.TINT -> TintBackdrop(current, dark)
                    AccentBackdropStyle.PHOTO -> PhotoBackdrop(current, surface, dark)
                }
            }
        }
    }
}

/** One flat colour: the washed accent seed. */
@Composable
private fun TintBackdrop(accent: ImageAccent, dark: Boolean) {
    val wash = remember(accent, dark) { accent.seed.washed(dark) }
    Box(modifier = Modifier.fillMaxSize().drawBehind { drawRect(wash) })
}

/**
 * The 48 px thumbnail stretched over the screen (bilinear, so already soft),
 * blurred further where `RenderEffect` exists, under a flat surface scrim
 * that keeps text legible.
 */
@Composable
private fun PhotoBackdrop(accent: ImageAccent, surface: Color, dark: Boolean) {
    val painter = remember(accent) { BitmapPainter(accent.thumbnail, filterQuality = FilterQuality.Low) }
    val scrim = surface.copy(alpha = if (dark) PHOTO_SCRIM_DARK else PHOTO_SCRIM_LIGHT)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(PHOTO_BLUR_RADIUS, BlurredEdgeTreatment.Unbounded)
                .paint(painter, contentScale = ContentScale.Crop),
        )
        Box(modifier = Modifier.fillMaxSize().drawBehind { drawRect(scrim) })
    }
}

private const val ACCENT_FADE_MILLIS = 500
private const val PHOTO_SCRIM_LIGHT = 0.72f
private const val PHOTO_SCRIM_DARK = 0.62f
private val PHOTO_BLUR_RADIUS = 36.dp

// ---------------------------------------------------------------------------
// Previews (synthetic accent: no file needed)
// ---------------------------------------------------------------------------

@Composable
private fun previewAccent(): ImageAccent {
    val bitmap = remember {
        androidx.compose.ui.graphics.ImageBitmap(4, 4).also { image ->
            val canvas = androidx.compose.ui.graphics.Canvas(image)
            val paint = androidx.compose.ui.graphics.Paint()
            listOf(Color(0xFFD9472B), Color(0xFF3E7D3A), Color(0xFFE8B84A), Color(0xFF6B3A7A)).forEachIndexed { i, c ->
                paint.color = c
                canvas.drawRect(0f, i.toFloat(), 4f, i + 1f, paint)
            }
        }
    }
    return ImageAccent(
        seed = Color(0xFFD9472B),
        swatches = listOf(Color(0xFFD9472B), Color(0xFF3E7D3A)),
        thumbnail = bitmap,
    )
}

@Preview(name = "Backdrop · tint", showBackground = true)
@Composable
private fun TintPreview() = MaterialTheme { AccentBackdrop(previewAccent(), AccentBackdropStyle.TINT) }

@Preview(name = "Backdrop · photo", showBackground = true)
@Composable
private fun PhotoPreview() = MaterialTheme { AccentBackdrop(previewAccent(), AccentBackdropStyle.PHOTO) }
