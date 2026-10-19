package com.llmlocal.recipekeeper.recipes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.recipekeeper.recipes.domain.PictureImporter
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * [PictureImporter] over `ContentResolver`: decodes the picked picture with
 * `inSampleSize` (bounds pass first, so a 4000×3000 photo never sits in
 * memory at full size), applies the EXIF orientation, scales the long edge
 * to at most `maxLongEdgePx` and writes a JPEG (q85) through a `.part`
 * file + rename. Temp files live under `cacheDir/recipe-editor/`;
 * [sweep] clears stale ones.
 */
class ContentResolverPictureImporter(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val editorRoot: File = File(context.cacheDir, EDITOR_DIR),
) : PictureImporter {

    override suspend fun import(uri: String, into: File, maxLongEdgePx: Int): Outcome<File> =
        withContext(dispatchers.io) {
            try {
                Outcome.Success(importBlocking(Uri.parse(uri), into, maxLongEdgePx))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Outcome.Failure(e)
            }
        }

    private fun importBlocking(uri: Uri, into: File, maxLongEdgePx: Int): File {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it ?: throw IOException("Cannot open $uri"), null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image: $uri")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxLongEdgePx)
        }
        val decoded = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it ?: throw IOException("Cannot open $uri"), null, options) }
            ?: throw IOException("Cannot decode $uri")
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        applyOrientation(matrix, orientation)
        val longEdge = maxOf(decoded.width, decoded.height)
        if (longEdge > maxLongEdgePx) matrix.postScale(maxLongEdgePx.toFloat() / longEdge, maxLongEdgePx.toFloat() / longEdge)
        val finalBitmap = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        into.parentFile?.mkdirs()
        val part = File(into.parentFile, into.name + ".part")
        try {
            part.outputStream().use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (!part.renameTo(into)) {
                part.copyTo(into, overwrite = true)
            }
        } finally {
            part.delete()
            if (finalBitmap !== decoded) finalBitmap.recycle()
            decoded.recycle()
        }
        return into
    }

    override fun sweep(olderThanMillis: Long) {
        editorRoot.walkBottomUp().forEach { file ->
            if (file == editorRoot) return@forEach
            if (file.isFile && file.lastModified() < olderThanMillis) file.delete()
            else if (file.isDirectory && file.list().isNullOrEmpty()) file.delete()
        }
    }

    companion object {
        const val EDITOR_DIR = "recipe-editor"
        private const val JPEG_QUALITY = 85

        /** Largest power of two that keeps the decoded long edge at or above [maxLongEdgePx]. */
        fun sampleSizeFor(width: Int, height: Int, maxLongEdgePx: Int): Int {
            var sample = 1
            var longEdge = maxOf(width, height)
            while (longEdge / 2 >= maxLongEdgePx) {
                sample *= 2
                longEdge /= 2
            }
            return sample
        }

        private fun applyOrientation(matrix: Matrix, orientation: Int) {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
        }
    }
}
