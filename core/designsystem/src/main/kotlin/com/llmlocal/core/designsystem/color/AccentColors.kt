package com.llmlocal.core.designsystem.color

import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import kotlin.math.min

/**
 * Pure-Kotlin colour maths behind [ImageAccent]: picks the colours a picture
 * is "about" and tames them into background-safe tones.
 *
 * Built on Material Color Utilities — the same pipeline Android uses to turn
 * a wallpaper into a theme seed: [QuantizerCelebi] reduces the pixels to a
 * weighted palette, [Score] ranks that palette by how much of the image each
 * hue covers and how colourful it is (dropping near-greys and hues under 1 %
 * of the image), and [Hct] re-tones the winners while keeping their hue.
 *
 * Everything here works on ARGB `Int`s so it has no Android or Compose
 * dependency and is unit-testable on the JVM.
 */
object AccentColors {

    /** Distinct colours the quantizer keeps before scoring; 128 is plenty for a ≤ 96 px thumbnail. */
    const val DEFAULT_MAX_COLORS = 128

    /**
     * The most representative colours of [pixels], best first; empty only for
     * an empty input. A picture with no colourful region (a greyscale photo)
     * falls back to unfiltered scoring so the caller still gets its dominant
     * neutral rather than nothing.
     */
    fun seeds(pixels: IntArray, desired: Int = 4, maxColors: Int = DEFAULT_MAX_COLORS): List<Int> {
        if (pixels.isEmpty()) return emptyList()
        val quantized = QuantizerCelebi.quantize(pixels, maxColors)
        if (quantized.isEmpty()) return emptyList()
        val scored = Score.score(quantized, desired = desired, fallbackColorArgb = null, filter = true)
        if (scored.isNotEmpty()) return scored
        return Score.score(quantized, desired = desired, fallbackColorArgb = null, filter = false)
    }

    /**
     * "Wipes out" [argb] into a surface-like background: same hue, chroma
     * capped low, tone pushed to the very light (or, in [dark] theme, very
     * dark) end so `onSurface` text keeps AA contrast on top of it.
     */
    fun washed(argb: Int, dark: Boolean): Int {
        val hct = Hct.fromInt(argb)
        return Hct.from(
            hct.hue,
            min(hct.chroma, if (dark) WASHED_DARK_MAX_CHROMA else WASHED_LIGHT_MAX_CHROMA),
            if (dark) WASHED_DARK_TONE else WASHED_LIGHT_TONE,
        ).toInt()
    }

    /** HCT tone (0 = black, 100 = white) of [argb]; exposed for tests and contrast checks. */
    fun tone(argb: Int): Double = Hct.fromInt(argb).tone

    /** HCT hue in degrees of [argb]. */
    fun hue(argb: Int): Double = Hct.fromInt(argb).hue

    private const val WASHED_LIGHT_TONE = 93.0
    private const val WASHED_DARK_TONE = 18.0
    private const val WASHED_LIGHT_MAX_CHROMA = 16.0
    private const val WASHED_DARK_MAX_CHROMA = 20.0
}
