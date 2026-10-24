package com.llmlocal.core.llm.model.discovery

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.model.ModelAccelerator
import com.llmlocal.core.model.ModelFamily
import org.junit.Test

class HuggingFaceModelDiscoveryTest {

    private val listing = """
        [
          {"id":"litert-community/gemma-4-E2B-it-litert-lm","tags":["litert-lm","region:us"]},
          {"id":"litert-community/Qwen3-4B","pipeline_tag":"text-generation","tags":["litert-lm"]},
          {"id":"litert-community/Llama-3.2-3B","pipeline_tag":"text-generation","gated":"manual","tags":["litert-lm"]},
          {"id":"litert-community/SmolVLM2-500M","pipeline_tag":"image-text-to-text","tags":["litert-lm"]},
          {"id":"litert-community/Kokoro-82M","pipeline_tag":"text-to-speech","tags":["litert-lm"]},
          {"id":"litert-community/EmbeddingGemma-300M","pipeline_tag":"feature-extraction","tags":["litert-lm"]}
        ]
    """.trimIndent()

    private val gemmaRepo = """
        {
          "id":"litert-community/gemma-4-E2B-it-litert-lm",
          "tags":["litert-lm","base_model:google/gemma-4-E2B-it","base_model:finetune:google/gemma-4-E2B-it","license:apache-2.0"],
          "cardData":{"license":"apache-2.0","base_model":["google/gemma-4-E2B-it"]},
          "lastModified":"2026-10-23T20:24:41.000Z",
          "siblings":[
            {"rfilename":".gitattributes","size":1500},
            {"rfilename":"gemma-4-E2B-it-gpu.litertlm","size":2008432640,"lfs":{"sha256":"a53a","size":2008432640}},
            {"rfilename":"gemma-4-E2B-it-web.litertlm","size":2008432640,"lfs":{"sha256":"3a08","size":2008432640}},
            {"rfilename":"gemma-4-E2B-it.litertlm","size":2588147712,"lfs":{"sha256":"1819","size":2588147712}},
            {"rfilename":"gemma-4-E2B-it_Google_Tensor_G5.litertlm","size":3113545589,"lfs":{"sha256":"af10","size":3113545589}},
            {"rfilename":"gemma-4-E2B-it_NPU_Uncompiled.litertlm","size":2614578293,"lfs":{"sha256":"b4f8","size":2614578293}},
            {"rfilename":"gemma-4-E2B-it_intel_LNL.litertlm","size":2960506880,"lfs":{"sha256":"dacc","size":2960506880}}
          ]
        }
    """.trimIndent()

    private val qwenRepo = """
        {
          "id":"litert-community/Qwen3-4B","pipeline_tag":"text-generation",
          "tags":["litert-lm","text-generation","base_model:Qwen/Qwen3-4B"],
          "cardData":{"license":"apache-2.0","base_model":"Qwen/Qwen3-4B"},
          "siblings":[
            {"rfilename":"qwen3_4b_channelwise_int8_float32kv.litertlm","lfs":{"size":4300000000,"sha256":"x"}},
            {"rfilename":"qwen3_4b_mixed_int4.litertlm","lfs":{"size":2500000000,"sha256":"y"}},
            {"rfilename":"Qwen3-4B.mediatek.mt6993.litertlm","lfs":{"size":2500000000,"sha256":"z"}}
          ]
        }
    """.trimIndent()

    private fun file(repo: String, name: String, size: Long = 1_000_000, pipeline: String? = "text-generation", base: String? = null) =
        HuggingFaceFile(
            repoId = "litert-community/$repo", filename = name, sizeBytes = size, sha256 = null, license = "apache-2.0",
            baseModel = base, repoTags = emptyList(), updatedAt = null, pipelineTag = pipeline,
        )

    @Test
    fun `repo listing keeps text-generation and untagged repos that are not gated`() {
        assertThat(HuggingFaceModelDiscovery.parseRepoIds(listing)).containsExactly(
            "litert-community/gemma-4-E2B-it-litert-lm",
            "litert-community/Qwen3-4B",
        ).inOrder()
    }

    @Test
    fun `repo files carry size, checksum, license, base model and timestamp`() {
        val files = HuggingFaceModelDiscovery.parseRepoFiles("litert-community/gemma-4-E2B-it-litert-lm", gemmaRepo)

        assertThat(files.map { it.filename }).hasSize(6)
        val generic = files.first { it.filename == "gemma-4-E2B-it.litertlm" }
        assertThat(generic.sizeBytes).isEqualTo(2_588_147_712L)
        assertThat(generic.sha256).isEqualTo("1819")
        assertThat(generic.license).isEqualTo("apache-2.0")
        assertThat(generic.baseModel).isEqualTo("google/gemma-4-E2B-it")
        assertThat(generic.updatedAt).isEqualTo("2026-10-23T20:24:41.000Z")
        assertThat(generic.pipelineTag).isNull()
    }

    @Test
    fun `mapper keeps generic and gpu bundles and drops web, intel and NPU ones`() {
        val mapper = HuggingFaceModelMapper()
        val files = HuggingFaceModelDiscovery.parseRepoFiles("litert-community/gemma-4-E2B-it-litert-lm", gemmaRepo)
        val descriptors = files.mapNotNull { mapper.toDescriptor(it, "https://huggingface.co") }

        assertThat(descriptors.map { it.id }).containsExactly(
            "gemma-4-E2B-it-litert-lm/gemma-4-E2B-it-gpu",
            "gemma-4-E2B-it-litert-lm/gemma-4-E2B-it",
        )
        val gpu = descriptors.first { it.accelerator == ModelAccelerator.GPU }
        assertThat(gpu.displayName).isEqualTo("Gemma 4 E2B IT (GPU)")
        assertThat(gpu.filename).isEqualTo("gemma-4-E2B-it-litert-lm__gemma-4-E2B-it-gpu.litertlm")
        assertThat(gpu.url).isEqualTo(
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-gpu.litertlm",
        )
        assertThat(gpu.family).isEqualTo(ModelFamily.INSTRUCT)
        assertThat(gpu.author).isEqualTo("Google")
        assertThat(gpu.sourceRepo).isEqualTo("litert-community/gemma-4-E2B-it-litert-lm")
        assertThat(gpu.tags).containsAtLeast("instruction-tuned", "gpu", "discovered")
        assertThat(gpu.minRamMb).isEqualTo(3_584)
        val cpu = descriptors.first { it.accelerator == ModelAccelerator.CPU }
        assertThat(cpu.displayName).isEqualTo("Gemma 4 E2B IT")
    }

    @Test
    fun `quantisation variants are named and device builds dropped`() {
        val mapper = HuggingFaceModelMapper()
        val files = HuggingFaceModelDiscovery.parseRepoFiles("litert-community/Qwen3-4B", qwenRepo)
        val descriptors = files.mapNotNull { mapper.toDescriptor(it, "https://h") }

        assertThat(descriptors.map { it.displayName }).containsExactly(
            "Qwen3 4B (channelwise int8 float32kv)",
            "Qwen3 4B (mixed int4)",
        )
        val int4 = descriptors.first { it.id == "Qwen3-4B/qwen3_4b_mixed_int4" }
        assertThat(int4.tags).containsAtLeast("quantised", "int4", "qwen")
        assertThat(int4.author).isEqualTo("Qwen")
        assertThat(int4.family).isEqualTo(ModelFamily.GENERAL)
    }

    @Test
    fun `context window, families and exclusions`() {
        val mapper = HuggingFaceModelMapper()
        val gemma3 = mapper.toDescriptor(file("Gemma3-1B-IT", "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm", base = "google/Gemma-3-1B-IT"), "https://h")!!
        assertThat(gemma3.contextTokens).isEqualTo(4096)
        assertThat(gemma3.displayName).isEqualTo("Gemma3 1B IT (q4)")
        assertThat(gemma3.family).isEqualTo(ModelFamily.INSTRUCT)

        val coder = mapper.toDescriptor(file("Qwen2.5-Coder-1.5B-Instruct", "Qwen2.5-Coder-1.5B-Instruct_int4.litertlm"), "https://h")!!
        assertThat(coder.family).isEqualTo(ModelFamily.CODE)
        assertThat(coder.displayName).isEqualTo("Qwen2.5 Coder 1.5B Instruct (int4)")

        val plain = mapper.toDescriptor(file("Jan-nano", "model.litertlm", base = "Menlo/Jan-nano"), "https://h")!!
        assertThat(plain.displayName).isEqualTo("Jan Nano")
        assertThat(plain.id).isEqualTo("Jan-nano/model")
        assertThat(plain.filename).isEqualTo("Jan-nano__model.litertlm")

        assertThat(mapper.toDescriptor(file("functiongemma-270m-ft-tiny-garden", "tiny_garden_q8_ekv1024.litertlm"), "https://h")).isNull()
        assertThat(mapper.toDescriptor(file("Qwen3.5-2B", "Qwen3.5-2B-VL_int8.litertlm"), "https://h")).isNull()
        assertThat(mapper.toDescriptor(file("Kokoro-82M", "kokoro_int8_gpu.litertlm", pipeline = "text-to-speech"), "https://h")).isNull()
        assertThat(mapper.toDescriptor(file("Gemma3-1B-IT-Tensor-NPU", "Gemma3-1B-IT_int4hadamard_aot_ekv4096_G4.litertlm"), "https://h")).isNull()
        assertThat(mapper.toDescriptor(file("big", "big.litertlm", size = 9L * 1024 * 1024 * 1024), "https://h")).isNull()
    }

    @Test
    fun `display names read naturally`() {
        assertThat(HuggingFaceModelMapper.displayName("gemma-4-E4B-it")).isEqualTo("Gemma 4 E4B IT")
        assertThat(HuggingFaceModelMapper.displayName("granite-4.1-3b")).isEqualTo("Granite 4.1 3B")
        assertThat(HuggingFaceModelMapper.displayName("Phi-3.5-mini-instruct")).isEqualTo("Phi 3.5 Mini Instruct")
        assertThat(HuggingFaceModelMapper.displayName("SmolLM2-1.7B-Instruct")).isEqualTo("SmolLM2 1.7B Instruct")
    }
}
