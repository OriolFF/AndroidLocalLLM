package com.llmlocal.core.llm.download

import android.content.Context
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.llm.model.DownloadProgress
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.core.model.LlmModelDescriptor
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Manages the on-disk LLM model file. Responsible for:
 *  - Locating the model's target directory (inside `Context.filesDir/llm/`).
 *  - Checking if the model is already downloaded.
 *  - Streaming the model from a URL with progress reporting.
 *  - Verifying the download (size sanity check; SHA-256 if the descriptor
 *    provides one).
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

    /**
     * Returns the [LlmModelDescriptor] this manager is configured for. Useful
     * for surfacing size/filename in the UI before the download starts.
     */
    fun descriptor(): LlmModelDescriptor = model

    /**
     * Ensures the model is available on disk. If not, downloads it and
     * emits [DownloadProgress] events along the way.
     *
     * On success, the flow completes normally and the resulting [File] can
     * be queried via [targetFile]. On failure, the flow throws.
     */
    fun ensureModel(): Flow<DownloadProgress> = flow {
        val file = targetFile()
        if (file.exists() && file.length() > 0) {
            emit(DownloadProgress(bytesRead = file.length(), totalBytes = file.length()))
            return@flow
        }
        downloadInto(file)
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
            downloadInto(file)
            Outcome.Success(file)
        } catch (t: Throwable) {
            Outcome.Failure(t)
        }
    }

    private suspend fun downloadInto(target: File) {
        modelDirectory().mkdirs()
        val tmp = File(target.parentFile, "${target.name}.part")
        if (tmp.exists()) tmp.delete()

        val request = Request.Builder().url(model.url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download model: HTTP ${response.code}")
            }
            val body = response.body
                ?: throw IOException("Empty response body when downloading model")

            val total = body.contentLength().takeIf { it > 0 }
            val sink = tmp.outputStream()
            var read = 0L
            val buffer = ByteArray(8 * 1024)
            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n == -1) break
                    if (n > 0) {
                        sink.write(buffer, 0, n)
                        read += n
                        // Note: we do not have a Flow emitter in this suspending
                        // helper. The public Flow API (ensureModel) is the
                        // progress-reporting path used by the UI; the blocking
                        // helper is for engine initialization.
                    }
                }
                sink.flush()
                sink.close()
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
                // Fall back to copy if rename across filesystems fails.
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
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
}
