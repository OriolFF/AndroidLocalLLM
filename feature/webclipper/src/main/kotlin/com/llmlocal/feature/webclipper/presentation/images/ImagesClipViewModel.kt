package com.llmlocal.feature.webclipper.presentation.images

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.feature.webclipper.domain.ClipSession
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.feature.webclipper.domain.usecase.DiscardPreparedImagesUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ObserveInputCapabilitiesUseCase
import com.llmlocal.feature.webclipper.domain.usecase.PrepareImagesUseCase
import com.llmlocal.feature.webclipper.presentation.WebClipperLabels
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Prepare/review screen of the images source. Contract frozen by I0; the
 * behaviour (picker launch, preparing, review, extract, discard, state
 * restore) arrives with stream I2, so [init] and [onIntent] do nothing yet.
 */
@Suppress("unused")
class ImagesClipViewModel(
    private val pendingUris: List<String>?,
    private val savedStateHandle: SavedStateHandle,
    private val config: WebClipConfig<*>,
    private val labels: WebClipperLabels,
    private val session: ClipSession,
    private val prepareImages: PrepareImagesUseCase,
    private val discardImages: DiscardPreparedImagesUseCase,
    private val observeCapabilities: ObserveInputCapabilitiesUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ImagesClipState(maxImages = config.imagePolicy.maxImages))
    val state: StateFlow<ImagesClipState> = _state.asStateFlow()

    private val _effects = Channel<ImagesClipEffect>(Channel.BUFFERED)
    val effects: Flow<ImagesClipEffect> = _effects.receiveAsFlow()

    fun onIntent(intent: ImagesClipIntent) {
        // I2
    }
}
