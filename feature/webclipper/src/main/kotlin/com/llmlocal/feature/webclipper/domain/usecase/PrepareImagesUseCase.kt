package com.llmlocal.feature.webclipper.domain.usecase

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.web.LocalImageImporter
import com.llmlocal.feature.webclipper.domain.ImagePolicy
import com.llmlocal.feature.webclipper.domain.TempPictureStore
import com.llmlocal.feature.webclipper.domain.input.ClipImage

/**
 * Turns picked content URIs into [ClipImage]s registered with the temp
 * store: sequential import (memory), optional tiling of tall pictures, cap at
 * `policy.maxImages`. Body arrives with stream I2.
 */
class PrepareImagesUseCase(
    private val importer: LocalImageImporter,
    private val tempPictures: TempPictureStore,
    private val dispatchers: DispatcherProvider,
) {

    /**
     * @property skipped URIs that could not be read
     * @property capped pictures were dropped to respect the policy cap
     */
    data class Result(val images: List<ClipImage>, val skipped: Int, val capped: Boolean)

    suspend operator fun invoke(uris: List<String>, policy: ImagePolicy): Result = TODO("I2")
}
