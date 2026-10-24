package com.llmlocal.feature.webclipper.domain.usecase

import com.llmlocal.core.llm.readiness.ModelReadiness
import com.llmlocal.core.model.ModelCapability
import com.llmlocal.feature.webclipper.domain.ReadinessSource
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the images source is usable: policy flag × model readiness ×
 * [ModelCapability.VISION]. `Checking`, `Downloading`, `Initializing` and
 * `Failed` all read as "no model" — the user has to visit the models screen
 * either way.
 */
class ObserveInputCapabilitiesUseCase(
    private val readiness: ReadinessSource,
    private val config: WebClipConfig<*>,
) {

    fun observe(scope: CoroutineScope): StateFlow<ImagesAvailability> {
        val source = readiness.observe(scope)
        return source
            .map(::availabilityOf)
            .stateIn(scope, SharingStarted.Eagerly, availabilityOf(source.value))
    }

    private fun availabilityOf(readiness: ModelReadiness): ImagesAvailability = when {
        !config.imagePolicy.enabled -> ImagesAvailability.Disabled
        readiness is ModelReadiness.Ready ->
            if (ModelCapability.VISION in readiness.descriptor.capabilities) {
                ImagesAvailability.Available
            } else {
                ImagesAvailability.ModelLacksVision
            }
        else -> ImagesAvailability.NoModel
    }
}
