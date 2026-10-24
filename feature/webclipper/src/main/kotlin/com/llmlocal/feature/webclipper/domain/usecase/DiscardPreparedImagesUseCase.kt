package com.llmlocal.feature.webclipper.domain.usecase

import com.llmlocal.feature.webclipper.domain.TempPictureStore
import com.llmlocal.feature.webclipper.domain.input.ClipImage

/** Deletes prepared pictures that will not be clipped (close, back, pick different). Body arrives with stream I2. */
class DiscardPreparedImagesUseCase(private val tempPictures: TempPictureStore) {

    operator fun invoke(images: List<ClipImage>): Unit = TODO("I2")
}
