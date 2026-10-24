package com.llmlocal.feature.webclipper.domain.usecase

import com.llmlocal.feature.webclipper.domain.TempPictureStore
import com.llmlocal.feature.webclipper.domain.input.ClipImage

/** Deletes prepared pictures that will not be clipped (close, back, cancel). */
class DiscardPreparedImagesUseCase(private val tempPictures: TempPictureStore) {

    operator fun invoke(images: List<ClipImage>) {
        images.forEach { tempPictures.delete(it.file) }
    }
}
