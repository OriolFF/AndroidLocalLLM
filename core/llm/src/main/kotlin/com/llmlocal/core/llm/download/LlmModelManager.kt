package com.llmlocal.core.llm.download

import android.content.Context
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.llm.model.DownloadProgress
import com.llmlocal.core.model.LlmModelDescriptor
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
 * Manages the on-disk LLM model files. Stateless w.r.t. which model: every
 * public method takes an [LlmModelDescriptor] so a single instance can serve
 * the entire catalog.
 *
 * Responsibilities:
 *  - Locating each model's target directory (inside `Context.filesDir/llm/`).
 *  - Checking if a model is already downloaded (`isInstalled`).
 *  - Listing all currently-installed models (`installedModelIds`).
 *  - Streaming a model from its URL with **real-time progress reporting**
 *    (bytes / total / speed / ETA) — consumers subscribe to the [download]
 *    flow and update their UI on every emission.
 *  - Verifying the download (size sanity check; SHA-256 if the descriptor
 *    provides one).
 *  - Supporting cancellation — if the consumer cancels its collection, the
 *    in-flight HTTP call is cancelled and the partial `.part` file is
 *    deleted.
 *  - Removing a model file from disk (`remove`).
 *
 * The class is a Koin `single` and runs all blocking work on
 * [DispatcherProvider.io].
 */
class LlmModelManager(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) {

    /** Directory inside `filesDir` that holds every downloaded model. */
    fun modelDirectory(): File = File(context.filesDir, MODEL_DIR_NAME)

    /** Absolute path of the final (non-`.part`) file for [descriptor]. */
    fun targetFile(descriptor: LlmModelDescriptor): File =
        File(modelDirectory(), descriptor.filename)

    /** True if [descriptor]'s file already exists locally with non-zero size. */
    suspend fun isInstalled(descriptor: LlmModelDescriptor): Boolean =
        withContext(dispatchers.io) {
            val file = targetFile(descriptor)
            file.exists() && file.length() > 0
        }

    /**
     * Returns the set of catalog ids whose files are present on disk. A
     * descriptor whose file exists but is not in [LlmModelCatalog.ALL] is
     * ignored (the model has been removed from the catalog).
     */
    suspend fun installedModelIds(): Set<String> = withContext(dispatchers.io) {
        val dir = modelDirectory()
        if (!dir.exists()) return@withContext emptySet()
        val onDisk = dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && it.length() > 0 }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
        com.llmlocal.core.llm.model.LlmModelCatalog.ALL
            .filter { it.filename in onDisk }
            .map { it.id }
            .toSet()
    }

    /**
     * Deletes the model file for [descriptor] from disk. Returns `true` if
     * the file was present and removed; `false` if there was nothing to
     * remove. Any in-progress `.part` file is also cleaned up.
     */
    suspend fun remove(descriptor: LlmModelDescriptor): Boolean = withContext(dispatchers.io) {
        val file = targetFile(descriptor)
        val tmp = File(file.parentFile, "${file.name}.part")
        var removed = false
        if (tmp.exists()) removed = tmp.delete() || removed
        if (file.exists()) removed = file.delete() || removed
        removed
    }

    /**
     * Downloads [descriptor] from its URL into `filesDir/llm/<descriptor.filename>`
     * and emits [DownloadProgress] events throughout the transfer.
     *
     * If the file already exists with non-zero size, the flow emits a single
     * synthetic 100% tick and completes — useful for the UI which can then
     * mark the model "installed" without doing a real download.
     *
     * On success, the flow completes normally and the resulting [File] can
     * be queried via [targetFile]. On failure, the flow throws. Cancelling
     * the collection aborts the download and deletes the partial `.part`
     * file.
     */
    fun download(descriptor: LlmModelDescriptor): Flow<DownloadProgress> = channelFlow {
        val file = targetFile(descriptor)
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
        downloadInto(descriptor, file, this)
    }.flowOn(dispatchers.io)

    /**
     * Downloads [descriptor] into [target] and emits progress into [scope].
     *
     * The HTTP call runs on OkHttp's dispatcher; the read loop runs on
     * [dispatchers.io]. Progress is throttled to one emission per
     * [PROGRESS_INTERVAL_MS] to avoid flooding the consumer.
     */
    private suspend fun downloadInto(
        descriptor: LlmModelDescriptor,
        target: File,
        scope: ProducerScope<DownloadProgress>?,
    ) {
        modelDirectory().mkdirs()
        val tmp = File(target.parentFile, "${target.name}.part")
        if (tmp.exists()) tmp.delete()

        val request = Request.Builder().url(descriptor.url).build()
        val call = httpClient.newCall(request)

        val startedAt = System.currentTimeMillis()
        var lastEmitAt = 0L
        var lastEmitBytes = 0L

        try {
            // Run the blocking call on OkHttp's thread pool and bridge back
            // via suspendCancellableCoroutine so cancellation propagates correctly.
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
                            // Cooperative cancellation check. The blocking
                            // socket read below can't observe coroutine
                            // cancellation, so without this an in-flight
                            // download can outlive the worker until the next
                            // byte arrives — and on a mid-flight cancel the
                            // OS usually closes the socket first, surfacing
                            // as SocketException instead of
                            // CancellationException. The outer catch below
                            // reclassifies that case; this check makes
                            // cancellation responsive in the steady state
                            // where bytes are still flowing.
                            currentCoroutineContext().ensureActive()
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

                if (descriptor.sha256 != null) {
                    val actual = sha256Of(tmp)
                    if (!actual.equals(descriptor.sha256, ignoreCase = true)) {
                        tmp.delete()
                        throw IOException(
                            "SHA-256 mismatch: expected ${descriptor.sha256}, got $actual",
                        )
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
            // If the surrounding coroutine has been cancelled — the user
            // closed the app and WorkManager is tearing the worker down,
            // or the foreground-service notification was dismissed — any
            // IO error from OkHttp (typically
            // SocketException("Software caused connection abort") or
            // EOFException) is a symptom of that cancellation rather than
            // a real network failure. The worker has a dedicated
            // catch (ce: CancellationException) branch that calls
            // markCancelled(); reclassify here so it picks the right
            // terminal state instead of falling through to
            // catch (t: Throwable) → markFailed().
            if (t !is CancellationException &&
                currentCoroutineContext()[Job]?.isActive == false
            ) {
                throw CancellationException("Download cancelled").apply {
                    initCause(t)
                }
            }
            throw t
        }
    }

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
        const val MODEL_DIR_NAME = "llm"

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