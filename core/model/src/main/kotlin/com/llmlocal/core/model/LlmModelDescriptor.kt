package com.llmlocal.core.model

/**
 * High-level grouping for models in the catalog — drives the UI's filter
 * chips (chat vs. instruction-tuned vs. code, etc.).
 */
enum class ModelFamily { GENERAL, CHAT, INSTRUCT, CODE, MULTIMODAL }

/**
 * Which accelerator a LiteRT-LM bundle was compiled for. The engine's
 * backend is chosen per build type (`debug` → CPU, `release` → GPU); a
 * bundle built for another accelerator fails at `Engine.initialize()`.
 */
enum class ModelAccelerator { CPU, GPU, NPU }

/**
 * What a model can take as input. Unlike [ModelFamily] (a filter-chip
 * grouping) this is a capability the engine and the UI branch on, e.g. the
 * "From images" source needs [VISION].
 */
enum class ModelCapability { TEXT, VISION, AUDIO }

/**
 * Describes a downloadable on-device LLM model.
 *
 * Descriptors come from two places: the curated built-in list
 * (`LlmModelCatalog.BUILT_IN`) and the models discovered on Hugging Face
 * (`ModelCatalogRepository`). The descriptor is also the key that the
 * [LlmModelManager][com.llmlocal.core.llm.download.LlmModelManager] uses
 * to download / locate / remove files on disk.
 *
 * @property id Stable identifier — primary key for selection, downloads,
 *   installed-set membership. Don't reuse an `id` across different files.
 * @property displayName User-facing name shown in the model management list.
 * @property description One-paragraph summary shown in the detail sheet.
 * @property url Direct download URL (HuggingFace resolve endpoint or CDN).
 * @property sizeBytes Approximate size of the model on disk, drives progress
 *   reporting and the size label.
 * @property filename Filename inside `Context.filesDir/llm/`. Unique per id.
 * @property sha256 Optional checksum; if present, the downloaded file is
 *   verified against it after the download completes.
 * @property tags Free-form labels shown as chips (e.g. "instruction-tuned",
 *   "quantised", "google").
 * @property license SPDX-ish license name shown in the detail sheet.
 * @property author Provider (Google, Meta, Microsoft…). Shown in the detail sheet.
 * @property minRamMb Hint for the UI's "won't run on this device" warning
 *   (informational only — not enforced).
 * @property family Coarse grouping; drives filter chips in the catalog list.
 * @property accelerator Hardware the bundle targets; see [ModelAccelerator].
 * @property sourceRepo Hugging Face repo the file was discovered in
 *   (`owner/name`), or `null` for a curated entry.
 * @property baseModel Upstream checkpoint the bundle was converted from
 *   (`google/gemma-4-E2B-it`), when known.
 * @property updatedAt ISO-8601 timestamp of the source's last change, when known.
 * @property contextTokens Context window (prompt + output tokens) the bundle
 *   was built with, when the source states it.
 * @property capabilities Input kinds the bundle can read; text only unless
 *   the bundle is known to ship the matching encoder.
 */
data class LlmModelDescriptor(
    val id: String,
    val displayName: String,
    val description: String,
    val url: String,
    val sizeBytes: Long,
    val filename: String,
    val sha256: String? = null,
    val tags: List<String> = emptyList(),
    val license: String = "Unknown",
    val author: String = "Unknown",
    val minRamMb: Int = 0,
    val family: ModelFamily = ModelFamily.GENERAL,
    val accelerator: ModelAccelerator = ModelAccelerator.CPU,
    val sourceRepo: String? = null,
    val baseModel: String? = null,
    val updatedAt: String? = null,
    val contextTokens: Int? = null,
    val capabilities: Set<ModelCapability> = setOf(ModelCapability.TEXT),
)