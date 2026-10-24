package com.llmlocal.core.llm.model

import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.core.model.ModelAccelerator
import com.llmlocal.core.model.ModelFamily

/**
 * Curated, verified on-device models that every app ships with.
 *
 * These entries are the floor of the catalog: they are available offline
 * before any discovery has run, and [DEFAULT_MODEL] is what a fresh install
 * selects. Every URL, size and checksum below was read from the Hugging
 * Face file listing of `litert-community` (2026-10-23); keep them in sync
 * with [BUILT_IN] when the upstream files change.
 *
 * The full list the apps show — built-ins plus whatever
 * [com.llmlocal.core.llm.model.catalog.ModelCatalogRepository] discovered —
 * lives on that repository. [ALL] and [findById] here only cover the
 * built-ins and remain for previews and tests.
 */
object LlmModelCatalog {

    private const val GEMMA_4_E2B_REPO = "litert-community/gemma-4-E2B-it-litert-lm"
    private const val GEMMA_4_E4B_REPO = "litert-community/gemma-4-E4B-it-litert-lm"

    /**
     * Default model — **Gemma 4 E2B IT**, the cross-platform CPU bundle.
     * Runs on every device the app supports (including the emulator).
     */
    val DEFAULT_MODEL: LlmModelDescriptor = LlmModelDescriptor(
        id = "gemma-4-E2B-it",
        displayName = "Gemma 4 E2B IT",
        description = "Google's compact instruction-tuned Gemma 4 (2B effective " +
            "parameters) in LiteRT-LM format. The cross-platform CPU bundle: " +
            "works on every device, slower than the GPU build. A good baseline.",
        url = "https://huggingface.co/$GEMMA_4_E2B_REPO/resolve/main/gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L,
        filename = "gemma-4-E2B-it.litertlm",
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        tags = listOf("instruction-tuned", "google", "cross-platform"),
        license = "apache-2.0",
        author = "Google",
        minRamMb = 4_096,
        family = ModelFamily.INSTRUCT,
        accelerator = ModelAccelerator.CPU,
        sourceRepo = GEMMA_4_E2B_REPO,
        baseModel = "google/gemma-4-E2B-it",
    )

    /** Gemma 4 E2B IT compiled for the GPU backend (release builds). */
    private val GEMMA_4_E2B_GPU: LlmModelDescriptor = LlmModelDescriptor(
        id = "gemma-4-E2B-it-gpu",
        displayName = "Gemma 4 E2B IT (GPU)",
        description = "The same Gemma 4 E2B IT weights, built for the GPU " +
            "backend: smaller and faster on phones with a capable GPU. Needs " +
            "a release build (debug builds run the engine on the CPU).",
        url = "https://huggingface.co/$GEMMA_4_E2B_REPO/resolve/main/gemma-4-E2B-it-gpu.litertlm",
        sizeBytes = 2_008_432_640L,
        filename = "gemma-4-E2B-it-gpu.litertlm",
        sha256 = "a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5",
        tags = listOf("instruction-tuned", "google", "gpu"),
        license = "apache-2.0",
        author = "Google",
        minRamMb = 4_096,
        family = ModelFamily.INSTRUCT,
        accelerator = ModelAccelerator.GPU,
        sourceRepo = GEMMA_4_E2B_REPO,
        baseModel = "google/gemma-4-E2B-it",
    )

    /** Gemma 4 E4B IT — the larger sibling, better quality at a higher cost. */
    private val GEMMA_4_E4B: LlmModelDescriptor = LlmModelDescriptor(
        id = "gemma-4-E4B-it",
        displayName = "Gemma 4 E4B IT",
        description = "Google's Gemma 4 with 4B effective parameters, " +
            "instruction-tuned. Noticeably better extraction and reasoning " +
            "than E2B; needs more RAM and roughly twice the time per answer.",
        url = "https://huggingface.co/$GEMMA_4_E4B_REPO/resolve/main/gemma-4-E4B-it.litertlm",
        sizeBytes = 3_659_530_240L,
        filename = "gemma-4-E4B-it.litertlm",
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        tags = listOf("instruction-tuned", "google", "cross-platform"),
        license = "apache-2.0",
        author = "Google",
        minRamMb = 6_144,
        family = ModelFamily.INSTRUCT,
        accelerator = ModelAccelerator.CPU,
        sourceRepo = GEMMA_4_E4B_REPO,
        baseModel = "google/gemma-4-E4B-it",
    )

    /** Curated models, in display order. Always present, even offline. */
    val BUILT_IN: List<LlmModelDescriptor> = listOf(
        DEFAULT_MODEL,
        GEMMA_4_E4B,
        GEMMA_4_E2B_GPU,
    )

    /** Alias of [BUILT_IN] kept for previews and tests; the live list is on the repository. */
    val ALL: List<LlmModelDescriptor> get() = BUILT_IN

    /** Lookup among the built-ins by stable [LlmModelDescriptor.id]; null if no such model. */
    fun findById(id: String): LlmModelDescriptor? = BUILT_IN.firstOrNull { it.id == id }
}
