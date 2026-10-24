package com.llmlocal.feature.webclipper.presentation.images

/** User actions on the images prepare/review screen. */
sealed interface ImagesClipIntent {
    data class ImagesPicked(val uris: List<String>) : ImagesClipIntent
    data object PickerCancelled : ImagesClipIntent
    data class Select(val key: String) : ImagesClipIntent
    data class Move(val from: Int, val to: Int) : ImagesClipIntent
    data class Remove(val key: String) : ImagesClipIntent
    data object AddMore : ImagesClipIntent
    data object Extract : ImagesClipIntent
    data object Close : ImagesClipIntent
    data object OpenModelManagement : ImagesClipIntent
}
