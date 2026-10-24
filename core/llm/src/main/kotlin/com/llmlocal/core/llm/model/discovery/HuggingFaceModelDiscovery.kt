package com.llmlocal.core.llm.model.discovery

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.model.LlmModelDescriptor
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * [ModelDiscoverySource] over the public Hugging Face Hub API.
 *
 * Two request shapes, no authentication:
 *
 *  1. `GET /api/models?author=<org>&limit=500` lists the org's repos; only
 *     text-generation repos (or untagged ones, like the Gemma 4 repos) that
 *     are not gated are kept, so the per-repo requests stay in the dozens.
 *  2. `GET /api/models/<repo>?blobs=true` lists each repo's files with
 *     sizes and LFS checksums; every `.litertlm` file becomes a candidate
 *     and [HuggingFaceModelMapper] decides which ones an app can run.
 *
 * Repo requests run a few at a time. Parsing is split into pure functions
 * ([parseRepoIds], [parseRepoFiles]) so it is unit-tested against captured
 * API responses.
 */
class HuggingFaceModelDiscovery(
    private val httpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider,
    private val organisation: String = DEFAULT_ORGANISATION,
    private val mapper: HuggingFaceModelMapper = HuggingFaceModelMapper(),
    private val baseUrl: String = HUB_BASE_URL,
    private val concurrency: Int = 4,
) : ModelDiscoverySource {

    override suspend fun discover(): List<LlmModelDescriptor> = withContext(dispatchers.io) {
        val repoIds = parseRepoIds(fetch("$baseUrl/api/models?author=$organisation&limit=500"))
        val gate = Semaphore(concurrency)
        val files = coroutineScope {
            repoIds.map { repoId ->
                async {
                    gate.withPermit { parseRepoFiles(repoId, fetch("$baseUrl/api/models/$repoId?blobs=true")) }
                }
            }.map { it.await() }
        }
        files.flatten()
            .mapNotNull { mapper.toDescriptor(it, baseUrl) }
            .distinctBy { it.id }
    }

    private fun fetch(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            return response.body.string()
        }
    }

    companion object {
        const val DEFAULT_ORGANISATION = "litert-community"
        const val HUB_BASE_URL = "https://huggingface.co"
        private const val BUNDLE_EXTENSION = ".litertlm"
        private const val TEXT_GENERATION = "text-generation"

        /**
         * Repo ids (`owner/name`) worth fetching: text-generation (or
         * untagged) repos that are not gated. Vision, speech, embedding and
         * image repos are dropped here; the mapper filters the files.
         */
        fun parseRepoIds(json: String): List<String> {
            val array = JSONArray(json)
            return (0 until array.length()).mapNotNull { i ->
                val repo = array.getJSONObject(i)
                val id = repo.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (isGated(repo)) return@mapNotNull null
                val pipeline = repo.optString("pipeline_tag").takeIf { it.isNotBlank() && it != "null" }
                val tags = repo.optJSONArray("tags").toStringList()
                val textGeneration = pipeline == TEXT_GENERATION || (pipeline == null && TEXT_GENERATION in tags)
                val untagged = pipeline == null
                id.takeIf { textGeneration || untagged }
            }
        }

        /** `gated` is `false`, or `"auto"` / `"manual"` when a licence click-through is required. */
        private fun isGated(repo: JSONObject): Boolean {
            val value = repo.opt("gated") ?: return false
            return when (value) {
                is Boolean -> value
                is String -> value.isNotBlank() && value != "false"
                else -> false
            }
        }

        /** Every `.litertlm` file of one repo, with the repo metadata the mapper needs. */
        fun parseRepoFiles(repoId: String, json: String): List<HuggingFaceFile> {
            val repo = JSONObject(json)
            val card = repo.optJSONObject("cardData")
            val tags = repo.optJSONArray("tags").toStringList()
            val baseModel = card?.opt("base_model")?.let { value ->
                when (value) {
                    is JSONArray -> value.optString(0).takeIf { it.isNotBlank() }
                    is String -> value.takeIf { it.isNotBlank() }
                    else -> null
                }
            } ?: tags.firstOrNull { it.startsWith("base_model:") && !it.startsWith("base_model:finetune:") }
                ?.removePrefix("base_model:")
            val license = card?.optString("license")?.takeIf { it.isNotBlank() }
                ?: tags.firstOrNull { it.startsWith("license:") }?.removePrefix("license:")
            val updatedAt = repo.optString("lastModified").takeIf { it.isNotBlank() }
            val pipeline = repo.optString("pipeline_tag").takeIf { it.isNotBlank() && it != "null" }
            if (isGated(repo)) return emptyList()
            val siblings = repo.optJSONArray("siblings") ?: return emptyList()
            return (0 until siblings.length()).mapNotNull { i ->
                val file = siblings.getJSONObject(i)
                val name = file.optString("rfilename")
                if (!name.endsWith(BUNDLE_EXTENSION, ignoreCase = true)) return@mapNotNull null
                val lfs = file.optJSONObject("lfs")
                HuggingFaceFile(
                    repoId = repoId,
                    filename = name,
                    sizeBytes = lfs?.optLong("size")?.takeIf { it > 0 } ?: file.optLong("size"),
                    sha256 = lfs?.optString("sha256")?.takeIf { it.isNotBlank() },
                    license = license,
                    baseModel = baseModel,
                    repoTags = tags,
                    updatedAt = updatedAt,
                    pipelineTag = pipeline,
                )
            }
        }

        private fun JSONArray?.toStringList(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { optString(it) }
    }
}

/** One `.litertlm` file as the Hub lists it, before mapping to a descriptor. */
data class HuggingFaceFile(
    val repoId: String,
    val filename: String,
    val sizeBytes: Long,
    val sha256: String?,
    val license: String?,
    val baseModel: String?,
    val repoTags: List<String>,
    val updatedAt: String?,
    val pipelineTag: String? = null,
) {
    /** Repo name without the owner. */
    val repoName: String get() = repoId.substringAfter('/')
}
