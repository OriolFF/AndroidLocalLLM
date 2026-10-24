package com.llmlocal.feature.modelmanagement.domain.usecase

import com.llmlocal.feature.modelmanagement.domain.DiscoveryResult
import com.llmlocal.feature.modelmanagement.domain.ModelDiscoverySource

/**
 * Asks the remote source for more models. `force = false` is the silent
 * check on screen open (skipped while the cache is fresh); `force = true`
 * is the user's explicit "Check for new models".
 */
class DiscoverModelsUseCase(private val source: ModelDiscoverySource) {
    suspend operator fun invoke(force: Boolean): DiscoveryResult = source.discover(force)
}
