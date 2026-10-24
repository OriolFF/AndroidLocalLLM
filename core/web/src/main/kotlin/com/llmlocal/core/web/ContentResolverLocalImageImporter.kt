package com.llmlocal.core.web

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * [LocalImageImporter] over `ContentResolver` (content:// and file:// URIs).
 *
 * `import` opens the URI twice — once for the bounds (and EXIF), once for a
 * sampled decode ([ImageGeometry.sampleSizeFor]) — then scales exactly,
 * applies the EXIF orientation, and writes a JPEG q85 through `<file>.part`
 * + rename. The SHA-256 is streamed over the written file. Decoding is
 * instrumented-only; the arithmetic lives in [ImageGeometry].
 */
class ContentResolverLocalImageImporter(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : LocalImageImporter {

    override suspend fun import(uri: String, into: File, maxLongEdgePx: Int): Outcome<ImportedImage> =
        withContext(dispatchers.io) {
            try {
                Outcome.Success(importBlocking(Uri.parse(uri), into, maxLongEdgePx))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.Failure(e)
            }
        }

    override suspend fun tile(
        source: ImportedImage,
        newFile: (index: Int) -> File,
        tallAspect: Double,
        overlap: Double,
    ): Outcome<List<ImportedImage>> = withContext(dispatchers.io) {
        try {
            val rects = ImageGeometry.tileRects(source.width, source.height, tallAspect, overlap)
            if (rects.size == 1) return@withContext Outcome.Success(listOf(source))
            val bitmap = BitmapFactory.decodeFile(source.file.path)
                ?: throw IOException("Cannot decode ${source.file.name}")
            try {
                Outcome.Success(
                    rects.mapIndexed { index, rect ->
                        val crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width, rect.height)
                        try {
                            writeJpeg(crop, newFile(index))
                        } finally {
                            if (crop !== bitmap) crop.recycle()
                        }
                    },
                )
            } finally {
                bitmap.recycle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Outcome.Failure(e)
        }
    }

    private fun importBlocking(uri: Uri, into: File, maxLongEdgePx: Int): ImportedImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image: $uri")

        val orientation = runCatching {
            open(uri).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val scale = ImageGeometry.targetScale(bounds.outWidth, bounds.outHeight, maxLongEdgePx)
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageGeometry.sampleSizeFor(bounds.outWidth, bounds.outHeight, scale)
        }
        val decoded = open(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Cannot decode $uri")

        var current: Bitmap = decoded
        try {
            // Exact scale from the sampled size to the target size, plus the EXIF transform, in one pass.
            val matrix = Matrix()
            val targetWidth = (bounds.outWidth * scale)
            val factor = (targetWidth / decoded.width).toFloat()
            if (factor < 1f) matrix.postScale(factor, factor)
            applyOrientation(matrix, orientation)
            if (!matrix.isIdentity) {
                val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (transformed !== decoded) {
                    decoded.recycle()
                    current = transformed
                }
            }
            return writeJpeg(current, into)
        } finally {
            current.recycle()
        }
    }

    /** Writes [bitmap] as JPEG q85 to [into] through `.part` + rename. */
    private fun writeJpeg(bitmap: Bitmap, into: File): ImportedImage {
        into.parentFile?.mkdirs()
        val part = File(into.parentFile, into.name + ".part")
        try {
            part.outputStream().buffered().use { out ->
                val flat = if (bitmap.hasAlpha()) flattenOnWhite(bitmap) else bitmap
                try {
                    if (!flat.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                        throw IOException("JPEG encoding failed")
                    }
                } finally {
                    if (flat !== bitmap) flat.recycle()
                }
            }
            if (into.exists()) into.delete()
            if (!part.renameTo(into)) throw IOException("Cannot move ${part.name} to ${into.name}")
        } finally {
            if (part.exists()) part.delete()
        }
        return ImportedImage(into, bitmap.width, bitmap.height, sha256Of(into))
    }

    /** JPEG has no alpha; a transparent PNG screenshot would turn black otherwise. */
    private fun flattenOnWhite(source: Bitmap): Bitmap {
        val flat = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        return flat
    }

    private fun open(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun applyOrientation(matrix: Matrix, orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
    }

    private companion object {
        const val JPEG_QUALITY = 85
        const val BUFFER_BYTES = 16 * 1024
    }
}
