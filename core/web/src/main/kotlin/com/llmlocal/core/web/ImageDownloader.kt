package com.llmlocal.core.web

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Downloads and resizes one page image for local storage.
 *
 * Sends `Referer` (the page URL) and a browser `User-Agent`, since many
 * CDNs refuse requests without them. The response is streamed into
 * `<into>.part` (rejecting a `Content-Length` above 15 MB up front and a
 * body that grows past it on the way), decoded with `inSampleSize` so the
 * long edge ends up at most [maxLongEdgePx], re-encoded as JPEG q85 into
 * [into], and the temp file is deleted. The call times out after 15 s and
 * is cancelled when the calling coroutine is.
 *
 * Pictures are storage only: nothing produced here is ever sent to the
 * LLM.
 */
class ImageDownloader(
    client: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) {

    /** Child client sharing the pool/dispatcher, with the image call timeout. */
    private val client: OkHttpClient = client.newBuilder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * @param url absolute `http(s)` image URL
     * @param referer page URL to send as `Referer`, or `null`
     * @param into destination file; its parent directory is created,
     *   and the file is overwritten on success
     * @param maxLongEdgePx resize bound for the longer edge
     * @param cookies value for the `Cookie` header (e.g. the WebView's cookies
     *   for the image host), or `null`; some CDNs bot-protect hot-linking
     * @return [Outcome.Success] with [into], or [Outcome.Failure] with the
     *   network/decode error. No partial file is left behind on failure.
     */
    suspend fun download(
        url: String,
        referer: String?,
        into: File,
        maxLongEdgePx: Int,
        cookies: String? = null,
    ): Outcome<File> = withContext(dispatchers.io) {
        if (url.isBlank()) return@withContext Outcome.Failure(IllegalArgumentException("blank url"))
        if (maxLongEdgePx <= 0) return@withContext Outcome.Failure(IllegalArgumentException("maxLongEdgePx must be > 0"))
        val part = File(into.parentFile, into.name + PART_SUFFIX)
        try {
            into.parentFile?.mkdirs()
            fetchTo(url, referer, cookies, part)
            resizeToJpeg(part, into, maxLongEdgePx)
            Outcome.Success(into)
        } catch (t: Throwable) {
            into.delete()
            Outcome.Failure(t)
        } finally {
            part.delete()
        }
    }

    /** Request headers for one attempt; `Referer`/`Cookie` only when non-blank. */
    internal fun buildRequest(url: String, referer: String?, cookies: String?): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .apply {
                if (!referer.isNullOrBlank()) header("Referer", referer)
                if (!cookies.isNullOrBlank()) header("Cookie", cookies)
            }
            .build()

    private suspend fun fetchTo(url: String, referer: String?, cookies: String?, part: File) {
        var response = client.newCall(buildRequest(url, referer, cookies)).await()
        // Some CDNs 403 on a foreign Referer: retry once without it.
        if (response.code == 403 && !referer.isNullOrBlank()) {
            response.close()
            response = client.newCall(buildRequest(url, null, cookies)).await()
        }
        response.use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body
            val declared = body.contentLength()
            if (declared > MAX_CONTENT_LENGTH) {
                throw IOException("Content-Length $declared exceeds $MAX_CONTENT_LENGTH for $url")
            }
            body.byteStream().use { source ->
                FileOutputStream(part).use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_CONTENT_LENGTH) throw IOException("Body exceeded $MAX_CONTENT_LENGTH for $url")
                        sink.write(buffer, 0, read)
                    }
                    sink.flush()
                }
            }
        }
    }

    /** Decode [part] with sub-sampling, scale to the bound, write JPEG q85 into [into]. */
    private fun resizeToJpeg(part: File, into: File, maxLongEdgePx: Int) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(part.absolutePath, bounds)
        val (width, height) = bounds.outWidth to bounds.outHeight
        if (width <= 0 || height <= 0) throw IOException("Not a decodable image: ${part.name}")

        val options = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(width, height, maxLongEdgePx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(part.absolutePath, options)
            ?: throw IOException("Bitmap decode failed: ${part.name}")
        val scaled = scaleToLongEdge(decoded, maxLongEdgePx)
        try {
            FileOutputStream(into).use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw IOException("JPEG encode failed for ${into.name}")
                }
            }
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }

    private fun scaleToLongEdge(bitmap: Bitmap, maxLongEdgePx: Int): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxLongEdgePx) return bitmap
        val ratio = maxLongEdgePx.toDouble() / longEdge
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return bitmap.scale(w, h)
    }

    /**
     * Largest power-of-two `inSampleSize` that keeps the decoded long edge
     * at or above [maxLongEdgePx]; [scaleToLongEdge] then brings it
     * exactly within the bound.
     */
    internal fun computeInSampleSize(width: Int, height: Int, maxLongEdgePx: Int): Int {
        val longEdge = maxOf(width, height)
        var sample = 1
        while (longEdge / (sample * 2) >= maxLongEdgePx) sample *= 2
        return sample
    }

    /** Suspend for an OkHttp call; cancelling the coroutine cancels the call. */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }

            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        })
        cont.invokeOnCancellation { cancel() }
    }

    private companion object {
        const val PART_SUFFIX = ".part"
        const val CALL_TIMEOUT_SECONDS = 15L
        const val MAX_CONTENT_LENGTH = 15L * 1024 * 1024
        const val JPEG_QUALITY = 85
        // Mobile Chrome UA — many image CDNs reject requests without a browser UA.
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}
