package com.llmlocal.core.designsystem.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb

/**
 * What a picture contributes to the screen around it.
 *
 * @property seed the single most representative colour (first of [swatches])
 * @property swatches up to four representative colours, best first; never empty
 * @property thumbnail a tiny software copy of the picture (longest side
 *   [ImageAccentLoader.THUMBNAIL_SIZE] px) for "ambient" backdrops that smear
 *   the photo itself; compared by identity
 */
@Immutable
data class ImageAccent(
    val seed: Color,
    val swatches: List<Color>,
    val thumbnail: ImageBitmap,
) {
    init {
        require(swatches.isNotEmpty()) { "ImageAccent needs at least one swatch" }
    }
}

/** Background-safe version of this colour: see [AccentColors.washed]. */
fun Color.washed(dark: Boolean): Color = Color(AccentColors.washed(toArgb(), dark))
