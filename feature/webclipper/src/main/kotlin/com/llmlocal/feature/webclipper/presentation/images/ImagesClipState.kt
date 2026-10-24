package com.llmlocal.feature.webclipper.presentation.images

import androidx.compose.runtime.Immutable
import com.llmlocal.feature.webclipper.domain.input.ClipImage
import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability

/**
 * State of the prepare/review screen of the images source.
 *
 * @property images prepared pictures in reading order
 * @property selectedKey the tile the move/remove actions apply to
 * @property isPreparing pictures are being imported
 * @property skipped how many picked pictures could not be read
 * @property capped pictures were dropped to respect the policy cap
 * @property availability whether the model can read images right now
 * @property maxImages cap from the policy, for the counter
 */
@Immutable
data class ImagesClipState(
    val images: List<ClipImage> = emptyList(),
    val selectedKey: String? = null,
    val isPreparing: Boolean = false,
    val skipped: Int = 0,
    val capped: Boolean = false,
    val availability: ImagesAvailability = ImagesAvailability.Available,
    val maxImages: Int = 3,
) {
    /** Two or more pictures need the user to confirm the order. */
    val needsReview: Boolean get() = images.size >= 2

    val canExtract: Boolean get() = images.isNotEmpty() && availability == ImagesAvailability.Available
}
