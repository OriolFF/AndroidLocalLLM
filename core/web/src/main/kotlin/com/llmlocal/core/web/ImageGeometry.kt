package com.llmlocal.core.web

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A crop of a picture, in pixels; [right] / [bottom] are exclusive. */
data class TileRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Pure arithmetic of [ContentResolverLocalImageImporter] (decode sampling,
 * target size, tile geometry), kept free of Android so the JVM can test it.
 */
object ImageGeometry {

    /** Height of a tile relative to its width: roughly a phone viewport (1:2.2). */
    const val TILE_ASPECT = 2.2

    /**
     * Short edge a tall strip keeps even when its long edge would exceed the
     * cap, so the text stays legible for [ContentResolverLocalImageImporter.tile].
     */
    const val MIN_SHORT_EDGE_PX = 1_080

    /** Hard bound on the decoded pixel count (memory), applied after the edge rules. */
    const val MAX_PIXELS = 12_000_000L

    /**
     * Factor (≤ 1) the decoded picture is scaled by: the long edge goes down
     * to [maxLongEdgePx], but never below [MIN_SHORT_EDGE_PX] on the short
     * edge (tall strips), and never above [MAX_PIXELS] in total.
     */
    fun targetScale(width: Int, height: Int, maxLongEdgePx: Int): Double {
        if (width <= 0 || height <= 0) return 1.0
        val long = max(width, height).toDouble()
        val short = min(width, height).toDouble()
        val byLongEdge = maxLongEdgePx / long
        val byShortEdge = MIN_SHORT_EDGE_PX / short
        var scale = min(1.0, max(byLongEdge, byShortEdge))
        val pixels = width.toDouble() * height * scale * scale
        if (pixels > MAX_PIXELS) scale *= sqrt(MAX_PIXELS / pixels)
        return scale
    }

    /** Largest power-of-two `inSampleSize` that still decodes at least [scale] of the picture. */
    fun sampleSizeFor(width: Int, height: Int, scale: Double): Int {
        if (width <= 0 || height <= 0 || scale >= 1.0 || scale <= 0.0) return 1
        var sample = 1
        while (min(width, height) / (sample * 2) >= min(width, height) * scale) sample *= 2
        return sample
    }

    /** Output size for a decoded [width] × [height] picture scaled by [scale] (at least 1 px). */
    fun scaledSize(width: Int, height: Int, scale: Double): Pair<Int, Int> =
        max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())

    /**
     * Overlapping crops of a picture taller than [tallAspect] × its width,
     * each [TILE_ASPECT] × width tall, evenly spread top to bottom with at
     * least [overlap] of a tile shared by neighbours. A picture that is not
     * tall comes back as one full rectangle.
     */
    fun tileRects(width: Int, height: Int, tallAspect: Double, overlap: Double = 0.10): List<TileRect> {
        val full = TileRect(0, 0, width, height)
        if (width <= 0 || height <= tallAspect * width) return listOf(full)
        val tileHeight = (width * TILE_ASPECT).roundToInt()
        if (tileHeight >= height) return listOf(full)
        val step = tileHeight * (1.0 - overlap.coerceIn(0.0, 0.5))
        val count = 1 + ceil((height - tileHeight) / step).toInt()
        val span = (height - tileHeight).toDouble()
        return List(count) { i ->
            val top = if (count == 1) 0 else (i * span / (count - 1)).roundToInt()
            TileRect(0, top, width, top + tileHeight)
        }
    }
}
