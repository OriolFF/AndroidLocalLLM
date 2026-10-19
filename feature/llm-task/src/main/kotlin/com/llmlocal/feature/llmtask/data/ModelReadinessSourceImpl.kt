package com.llmlocal.feature.llmtask.data

import com.llmlocal.core.llm.readiness.ModelReadiness
import com.llmlocal.core.llm.readiness.ModelReadinessObserver
import com.llmlocal.feature.llmtask.domain.ModelAvailability
import com.llmlocal.feature.llmtask.domain.ModelReadinessSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Adapts the shared [ModelReadinessObserver] to [ModelReadinessSource]. */
internal class ModelReadinessSourceImpl(
    private val observer: ModelReadinessObserver,
) : ModelReadinessSource {

    override fun observe(scope: CoroutineScope): StateFlow<ModelAvailability> {
        val upstream = observer.observe(scope)
        return upstream
            .map { it.toAvailability() }
            .stateIn(scope, SharingStarted.Eagerly, upstream.value.toAvailability())
    }

    override suspend fun recheck() = observer.recheck()
}

internal fun ModelReadiness.toAvailability(): ModelAvailability = when (this) {
    ModelReadiness.Checking -> ModelAvailability.Checking
    ModelReadiness.NoModel -> ModelAvailability.NoModel
    is ModelReadiness.Downloading -> ModelAvailability.Downloading
    ModelReadiness.Initializing -> ModelAvailability.Initializing
    is ModelReadiness.Ready -> ModelAvailability.Ready
    is ModelReadiness.Failed -> ModelAvailability.Failed(reason, installed = descriptor != null)
}
