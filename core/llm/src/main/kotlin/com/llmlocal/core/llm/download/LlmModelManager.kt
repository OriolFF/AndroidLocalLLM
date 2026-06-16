package com.llmlocal.core.llm.download

import android.content.Context
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.llm.model.DownloadProgress
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.core.model.LlmModelDescriptor
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Manages the on-disk LLM model file. Responsible for:
 *  - Locating the model's target directory (inside `Context.filesDir/llm/`).
 *  - Checking if the model is already downloaded.
 *  - Streaming the model from a URL with **real-time progress reporting**
 *    (bytes / total / speed / ETA) — the UI subscribes to the
 *    [ensureModel] flow and updates its banner on every emission.
 *  - Verifying the download (size sanity check; SHA-256 if the descriptor
 *    provides one).
 *  - Supporting cancellation — if the consumer of [ensureModel] cancels its
 *    collection, the in-flight HTTP call is cancelled and the partial
 *    `.part` file is deleted.
 *
 * The class is stateless: a single instance can serve the entire app via
 * Koin. All blocking work runs on [DispatcherProvider.io].
 */
class LlmModelManager(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider,
    private val model: LlmModelDescriptor = LlmModelCatalog.DEFAULT_MODEL,
) {

    /** Returns the absolute path of the model file once it is on disk. */
    fun targetFile(): File = File(modelDirectory(), model.filename)

    /** True if the model file already exists locally. */
    suspend fun isModelAvailable(): Boolean = withContext(dispatchers.io) {
        targetFile().exists() && targetFile().length() > 0
    }

    /** Human-readable size (e.g. "2.58 GB") for the UI. */
    fun humanSize(): String = humanReadableBytes(model.sizeBytes)

    /**
     * Returns the [LlmModelDescriptor] this manager is configured for. Useful
     * for surfacing size/filename in the UI before the download starts.
     */
    fun descriptor(): LlmModelDescriptor = model

    /**
     * Ensures the model is available on disk. If not, downloads it and
     * emits [DownloadProgress] events throughout the transfer.
     *
     * On success, the flow completes normally and the resulting [File] can
     * be queried via [targetFile]. On failure, the flow throws.
     * Cancelling the collection aborts the download and deletes the
     * partial file.
     */
    fun ensureModel(): Flow<DownloadProgress> = channelFlow {
        val file = targetFile()
        if (file.exists() && file.length() > 0) {
            send(
                DownloadProgress(
                    bytesRead = file.length(),
                    totalBytes = file.length(),
                    speedBytesPerSec = 0,
                    elapsedMs = 0,
                )
            )
            return@channelFlow
        }
        downloadInto(file, this)
    }.flowOn(dispatchers.io)

    /**
     * Convenience: ensures the model is downloaded and returns the [File]
     * once ready. Use this from [com.llmlocal.core.llm.engine.LlmEngine]
     * implementations.
     */
    suspend fun ensureModelBlocking(): Outcome<File> = withContext(dispatchers.io) {
        val file = targetFile()
        if (file.exists() && file.length() > 0) return@withContext Outcome.Success(file)
        try {
            downloadInto(file, scope = null)
            Outcome.Success(file)
        } catch (t: Throwable) {
            Outcome.Failure(t)
        }
    }

    /**
     * Downloads [target] from [model].url and emits progress into [scope].
     *
     * The HTTP call runs on OkHttp's dispatcher; the read loop runs on
     * [dispatchers.io]. Progress is throttled to one emission per
     * [PROGRESS_INTERVAL_MS] to avoid flooding the consumer.
     */
    private suspend fun downloadInto(target: File, scope: ProducerScope<DownloadProgress>?) {
        modelDirectory().mkdirs()
        val tmp = File(target.parentFile, "${target.name}.part")
        if (tmp.exists()) tmp.delete()

        val request = Request.Builder().url(model.url).build()
        val call = httpClient.newCall(request)

        val startedAt = System.currentTimeMillis()
        var lastEmitAt = 0L
        var lastEmitBytes = 0L

        try {
            // Run the blocking call on OkHttp's thread pool and bridge back
            // via suspendCoroutine so cancellation propagates correctly.
            val response: Response = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { runCatching { call.cancel() } }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWith(Result.failure(e))
                    }
                    override fun onResponse(call: Call, response: Response) {
                        if (cont.isActive) cont.resumeWith(Result.success(response))
                    }
                })
            }

            response.use { res ->
                if (!res.isSuccessful) {
                    throw IOException("Failed to download model: HTTP ${res.code}")
                }
                val body = res.body
                    ?: throw IOException("Empty response body when downloading model")

                val total = body.contentLength().takeIf { it > 0 }
                val sink = tmp.outputStream()
                var read = 0L
                val buffer = ByteArray(8 * 1024)

                try {
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            if (n > 0) {
                                sink.write(buffer, 0, n)
                                read += n
                                if (scope != null) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmitAt >= PROGRESS_INTERVAL_MS) {
                                        val deltaMs = (now - lastEmitAt).coerceAtLeast(1)
                                        val deltaBytes = read - lastEmitBytes
                                        val speed = deltaBytes * 1000L / deltaMs
                                        scope.trySend(
                                            DownloadProgress(
                                                bytesRead = read,
                                                totalBytes = total,
                                                speedBytesPerSec = speed,
                                                elapsedMs = now - startedAt,
                                            )
                                        )
                                        lastEmitAt = now
                                        lastEmitBytes = read
                                    }
                                }
                            }
                        }
                        sink.flush()
                        sink.close()
                    }
                } catch (ce: CancellationException) {
                    runCatching { tmp.delete() }
                    throw ce
                }

                if (total != null && read != total) {
                    tmp.delete()
                    throw IOException("Truncated download: got $read, expected $total")
                }

                if (model.sha256 != null) {
                    val actual = sha256Of(tmp)
                    if (!actual.equals(model.sha256, ignoreCase = true)) {
                        tmp.delete()
                        throw IOException("SHA-256 mismatch: expected ${model.sha256}, got $actual")
                    }
                }

                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }

                // Final 100% tick.
                scope?.trySend(
                    DownloadProgress(
                        bytesRead = read,
                        totalBytes = total ?: read,
                        speedBytesPerSec = 0,
                        elapsedMs = System.currentTimeMillis() - startedAt,
                    )
                )
            }
        } catch (t: Throwable) {
            runCatching { tmp.delete() }
            throw t
        }
    }

    private fun modelDirectory(): File = File(context.filesDir, "llm")

    private fun sha256Of(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                if (n > 0) digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PROGRESS_INTERVAL_MS = 250L

        /** Formats byte counts as "1.2 MB", "543 KB", "2.58 GB". */
        fun humanReadableBytes(bytes: Long): String = when {
            bytes >= 1L * 1024 * 1024 * 1024 ->
                "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
            bytes >= 1L * 1024 * 1024 ->
                "%.1f MB".format(bytes / 1024.0 / 1024.0)
            bytes >= 1L * 1024 ->
                "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
