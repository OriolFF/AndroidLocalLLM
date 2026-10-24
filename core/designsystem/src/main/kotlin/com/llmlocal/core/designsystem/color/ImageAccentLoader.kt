package com.llmlocal.core.designsystem.color

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import java.io.File

/**
 * Decodes a picture file into an [ImageAccent] cheaply: the JPEG is
 * subsampled straight to ≤ [ANALYSIS_SIZE] px on its longest side (so a
 * 4000 px photo costs a few milliseconds and ~36 KB), its pixels go through
 * [AccentColors.seeds], and a [THUMBNAIL_SIZE] px copy is kept for ambient
 * backdrops. Results are memoised per file (path + size + mtime) in a small
 * process-wide cache, so returning to a recipe never re-decodes.
 *
 * Call from a background dispatcher; see `rememberImageAccent` for the
 * Compose entry point.
 */
object ImageAccentLoader {

    /** Longest side of the bitmap the colours are read from. */
    const val ANALYSIS_SIZE = 96

    /** Longest side of [ImageAccent.thumbnail]. */
    const val THUMBNAIL_SIZE = 48

    private const val CACHE_ENTRIES = 32

    private val cache = LruCache<String, ImageAccent>(CACHE_ENTRIES)

    /** The cached accent of [file], if it was already computed; never decodes. */
    fun peek(file: File): ImageAccent? = cache.get(cacheKey(file))

    /** The accent of [file], or `null` when the file is missing or not a decodable image. */
    fun load(file: File): ImageAccent? {
        if (!file.isFile) return null
        val key = cacheKey(file)
        cache.get(key)?.let { return it }
        val accent = decode(file) ?: return null
        cache.put(key, accent)
        return accent
    }

    /** The accent of an already decoded [bitmap] (any size; it is downscaled for analysis). */
    fun fromBitmap(bitmap: Bitmap): ImageAccent? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val analysis = scaled(bitmap, ANALYSIS_SIZE)
        val pixels = IntArray(analysis.width * analysis.height)
        analysis.getPixels(pixels, 0, analysis.width, 0, 0, analysis.width, analysis.height)
        val seeds = AccentColors.seeds(pixels)
        if (seeds.isEmpty()) return null
        val thumbnail = scaled(analysis, THUMBNAIL_SIZE)
        return ImageAccent(
            seed = Color(seeds.first()),
            swatches = seeds.map { Color(it) },
            thumbnail = thumbnail.asImageBitmap(),
        )
    }

    private fun decode(file: File): ImageAccent? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight), ANALYSIS_SIZE)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        return fromBitmap(bitmap)
    }

    /** Largest power-of-two subsampling that keeps the longest side ≥ [target]. */
    internal fun sampleSize(longestSide: Int, target: Int): Int {
        var sample = 1
        while (longestSide / (sample * 2) >= target) sample *= 2
        return sample
    }

    private fun scaled(source: Bitmap, longestSide: Int): Bitmap {
        val scale = longestSide.toFloat() / maxOf(source.width, source.height)
        if (scale >= 1f) return source
        val w = maxOf(1, (source.width * scale).toInt())
        val h = maxOf(1, (source.height * scale).toInt())
        return source.scale(w, h)
    }

    private fun cacheKey(file: File): String = "${file.path}:${file.length()}:${file.lastModified()}"
}
