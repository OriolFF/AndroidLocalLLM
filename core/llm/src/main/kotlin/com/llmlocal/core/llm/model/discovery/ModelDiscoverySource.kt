package com.llmlocal.core.llm.model.discovery

import com.llmlocal.core.model.LlmModelDescriptor

/**
 * Finds downloadable LiteRT-LM models somewhere remote. Implementations
 * throw on network or parse failures; the repository turns that into an
 * `Outcome.Failure` and keeps the cached list.
 */
interface ModelDiscoverySource {
    suspend fun discover(): List<LlmModelDescriptor>
}
