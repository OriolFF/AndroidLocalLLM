package com.llmlocal.core.llm.model.discovery

import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.core.model.ModelAccelerator
import com.llmlocal.core.model.ModelFamily
import java.util.Locale

/**
 * Turns a [HuggingFaceFile] into an [LlmModelDescriptor], or `null` when
 * the file is not something this app can run:
 *
 *  - only text-generation repos (or untagged ones, e.g. the Gemma 4 repos)
 *    are considered; embedding, function-calling and vision bundles are
 *    dropped by name;
 *  - `-web`, Intel, uncompiled-NPU and vendor NPU bundles (`_Google_Tensor_*`,
 *    `_Qualcomm_*`, `sm8750`, `mt6993`, `aot`, …) need runtimes the apps do
 *    not ship, so only the generic (CPU) and GPU bundles are kept;
 *  - files above [maxSizeBytes] will not fit a phone.
 *
 * Ids are `<repo name>/<file stem>` and on-disk filenames are prefixed
 * with the repo name, because several repos ship a bare `model.litertlm`.
 * A discovered copy of a curated entry is dropped by URL in the repository.
 */
class HuggingFaceModelMapper(
    private val maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES,
) {

    fun toDescriptor(file: HuggingFaceFile, hubBaseUrl: String): LlmModelDescriptor? {
        if (file.pipelineTag != null && file.pipelineTag != TEXT_GENERATION) return null
        val stem = file.filename.removeSuffix(BUNDLE_EXTENSION)
        val repoName = file.repoName
        val haystack = "${repoName}/$stem".lowercase(Locale.ROOT)
        if (EXCLUDED_NAME_PARTS.any { it in haystack }) return null
        if (file.repoTags.any { it in EXCLUDED_TAGS }) return null
        if (file.sizeBytes <= 0 || file.sizeBytes > maxSizeBytes) return null
        val fileTokens = tokens(stem)
        if (fileTokens.any { token -> DEVICE_SPECIFIC.any { it.matches(token) } }) return null
        if (fileTokens.any { it in VISION_TOKENS }) return null

        val accelerator = if ("gpu" in fileTokens) ModelAccelerator.GPU else ModelAccelerator.CPU
        val variant = fileTokens.filter { token -> VARIANT_TOKENS.any { it.matches(token) } && token != "gpu" }
        val quantised = variant.any { QUANTISED.matches(it) } || fileTokens.any { QUANTISED.matches(it) }
        val contextTokens = fileTokens.firstNotNullOfOrNull { CONTEXT.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
        val author = file.baseModel?.substringBefore('/')?.takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.titlecase(Locale.ROOT) }
            ?: file.repoId.substringBefore('/')
        val repoTokens = tokens(repoName)
        val nameTokens = repoTokens + fileTokens
        val family = when {
            nameTokens.any { it in CODE_TOKENS } -> ModelFamily.CODE
            nameTokens.any { it in INSTRUCT_TOKENS } -> ModelFamily.INSTRUCT
            else -> ModelFamily.GENERAL
        }
        val tags = buildList {
            if (family == ModelFamily.INSTRUCT) add("instruction-tuned")
            if (nameTokens.any { it in REASONING_TOKENS }) add("reasoning")
            add(author.lowercase(Locale.ROOT))
            if (accelerator == ModelAccelerator.GPU) add("gpu") else add("cross-platform")
            if (quantised) add("quantised")
            variant.filter { it != "mixed" && it != "dynamic" }.take(2).forEach { add(it) }
            add("discovered")
        }.distinct()
        val sizeMb = file.sizeBytes / (1024 * 1024)
        val baseName = displayName(cleanRepoName(repoName))
        val qualifier = buildList {
            addAll(variant)
            if (accelerator == ModelAccelerator.GPU) add("GPU")
        }
        return LlmModelDescriptor(
            id = "$repoName/$stem",
            displayName = if (qualifier.isEmpty()) baseName else "$baseName (${qualifier.joinToString(" ")})",
            description = "${file.repoId} · ${file.baseModel ?: stem}",
            url = "$hubBaseUrl/${file.repoId}/resolve/main/${file.filename}",
            sizeBytes = file.sizeBytes,
            filename = "${repoName}__${file.filename}",
            sha256 = file.sha256,
            tags = tags,
            license = file.license ?: "Unknown",
            author = author,
            minRamMb = minRamMb(sizeMb),
            family = family,
            accelerator = accelerator,
            sourceRepo = file.repoId,
            baseModel = file.baseModel,
            updatedAt = file.updatedAt,
            contextTokens = contextTokens,
        )
    }

    companion object {
        const val DEFAULT_MAX_SIZE_BYTES: Long = 8L * 1024 * 1024 * 1024
        private const val BUNDLE_EXTENSION = ".litertlm"
        private const val TEXT_GENERATION = "text-generation"
        private val EXCLUDED_NAME_PARTS = listOf("embedding", "-web", "functiongemma", "function-call", "npu")
        private val EXCLUDED_TAGS = setOf("function-calling", "sentence-similarity", "feature-extraction")
        private val DEVICE_SPECIFIC = listOf(
            Regex("sm\\d{4}"), Regex("mt\\d{4}"), Regex("qcs\\d{4}"), Regex("g\\d"), Regex("aot"),
            Regex("qualcomm"), Regex("mediatek"), Regex("tensor"), Regex("google"), Regex("intel"),
            Regex("lnl"), Regex("ptl"), Regex("samsung"),
        )
        private val VISION_TOKENS = setOf("vision", "vl", "int8vis")
        private val VARIANT_TOKENS = listOf(
            Regex("mixed"), Regex("dynamic"), Regex("int[1-8]"), Regex("q[1-8]"), Regex("wi[1-8][a-z0-9]*"),
            Regex("fp16"), Regex("f16"), Regex("f32"), Regex("fp32"), Regex("afp32"), Regex("gpu"),
            Regex("block\\d+"), Regex("nothink"), Regex("channelwise"), Regex("float32kv"), Regex("sdpa"), Regex("fp32act"),
        )
        private val QUANTISED = Regex("int[1-8]|q[1-8]|wi[1-8][a-z0-9]*|int2pc|ternary")
        private val CONTEXT = Regex("(?:ekv|c)(\\d{3,6})")
        private val CODE_TOKENS = setOf("coder", "code", "codegemma")
        private val INSTRUCT_TOKENS = setOf("it", "instruct", "chat", "sft")
        private val REASONING_TOKENS = setOf("thinking", "think", "reasoning", "r1")

        /** Lower-case alphanumeric tokens of a name; dots inside numbers are kept (`1.7b`). */
        fun tokens(name: String): List<String> =
            name.lowercase(Locale.ROOT).split('-', '_', ' ', '.').filter { it.isNotBlank() }.let { raw ->
                // Re-join "1.7b"-style version numbers split by the dot.
                val out = mutableListOf<String>()
                var i = 0
                while (i < raw.size) {
                    val t = raw[i]
                    if (i + 1 < raw.size && t.all { it.isDigit() } && raw[i + 1].first().isDigit() &&
                        name.lowercase(Locale.ROOT).contains("$t.${raw[i + 1]}")
                    ) {
                        out += "$t.${raw[i + 1]}"
                        i += 2
                    } else {
                        out += t
                        i++
                    }
                }
                out
            }

        /** `gemma-4-E4B-it-litert-lm` → `gemma-4-E4B-it`. */
        fun cleanRepoName(repoName: String): String = repoName
            .removeSuffix(".litertlm")
            .removeSuffix("-litert-lm")
            .removeSuffix("-litertlm")
            .removeSuffix("-LiteRT")

        /** Model weight budget plus working memory: ~1.25 × file size + 1 GB headroom, rounded up to 512 MB. */
        fun minRamMb(sizeMb: Long): Int {
            val raw = sizeMb + sizeMb / 4 + 1024
            return (((raw + 511) / 512) * 512).toInt()
        }

        /** `gemma-4-E4B-it` → `Gemma 4 E4B IT`; `Qwen3-4B` → `Qwen3 4B`; `granite-4.1-3b` → `Granite 4.1 3B`. */
        fun displayName(name: String): String =
            name.split('-', '_', ' ').filter { it.isNotBlank() }.joinToString(" ") { word ->
                when {
                    word.equals("it", ignoreCase = true) -> "IT"
                    word.any { it.isUpperCase() } -> word
                    word.all { it.isDigit() || it == '.' } -> word
                    word.length <= 4 && word.any { it.isDigit() } -> word.uppercase(Locale.ROOT)
                    else -> word.replaceFirstChar { it.titlecase(Locale.ROOT) }
                }
            }
    }
}
