package com.llmlocal.feature.webclipper.domain.input

/** Whether the "From images" source can run right now. */
enum class ImagesAvailability { Available, NoModel, ModelLacksVision, Disabled }
