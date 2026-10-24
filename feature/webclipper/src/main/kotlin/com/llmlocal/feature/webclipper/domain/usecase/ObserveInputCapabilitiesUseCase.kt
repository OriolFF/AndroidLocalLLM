package com.llmlocal.feature.webclipper.domain.usecase

import com.llmlocal.feature.webclipper.domain.ReadinessSource
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Whether the images source is usable: policy flag × model readiness × `VISION`. Body arrives with stream I2. */
class ObserveInputCapabilitiesUseCase(
    private val readiness: ReadinessSource,
    private val config: WebClipConfig<*>,
) {

    fun observe(scope: CoroutineScope): StateFlow<ImagesAvailability> = TODO("I2")
}
