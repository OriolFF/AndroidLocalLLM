package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the library's "From images" source can run. Stub until stream I3,
 * which wraps the feature's `ObserveInputCapabilitiesUseCase`; until then the
 * source is always [ImagesAvailability.Disabled] (hidden).
 */
class ObserveImagesAvailabilityUseCase {
    private val disabled: StateFlow<ImagesAvailability> = MutableStateFlow(ImagesAvailability.Disabled)

    operator fun invoke(): StateFlow<ImagesAvailability> = disabled
}
