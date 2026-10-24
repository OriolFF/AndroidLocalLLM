package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability
import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatus
import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatusSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Whether the library's "From images" source can run: the build flag
 * ([enabled], `ImagePolicy.enabled`) × the selected model's readiness and
 * `VISION` capability, read from the library's own [LibraryModelStatusSource].
 *
 * Same four-way mapping as the feature's `ObserveInputCapabilitiesUseCase`
 * (kept app-side because that one is still a stub in this branch; the lead
 * can swap the body for a delegation at merge).
 */
class ObserveImagesAvailabilityUseCase(
    private val source: LibraryModelStatusSource,
    private val enabled: Boolean,
) {
    operator fun invoke(): Flow<ImagesAvailability> = source.status
        .map { status ->
            when {
                !enabled -> ImagesAvailability.Disabled
                status is LibraryModelStatus.Ready ->
                    if (status.supportsImages) ImagesAvailability.Available else ImagesAvailability.ModelLacksVision
                else -> ImagesAvailability.NoModel
            }
        }
        .distinctUntilChanged()
}
