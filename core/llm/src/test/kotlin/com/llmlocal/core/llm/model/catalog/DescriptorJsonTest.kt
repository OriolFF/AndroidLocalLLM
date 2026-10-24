package com.llmlocal.core.llm.model.catalog

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.llm.model.LlmModelCatalog
import org.junit.Test

class DescriptorJsonTest {

    @Test
    fun `round-trips every field`() {
        val models = LlmModelCatalog.BUILT_IN.map { it.copy(updatedAt = "2026-10-23T00:00:00Z") }
        assertThat(DescriptorJson.decode(DescriptorJson.encode(models))).isEqualTo(models)
    }

    @Test
    fun `tolerates blanks and unknown enum names`() {
        assertThat(DescriptorJson.decode("")).isEmpty()
        val decoded = DescriptorJson.decode(
            """[{"id":"x","url":"https://x","filename":"x.litertlm","family":"WHATEVER","accelerator":"TPU"},{"id":""}]""",
        )
        assertThat(decoded).hasSize(1)
        assertThat(decoded.single().displayName).isEqualTo("x")
    }
}
