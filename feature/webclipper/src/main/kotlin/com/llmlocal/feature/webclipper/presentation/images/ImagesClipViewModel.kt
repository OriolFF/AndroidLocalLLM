package com.llmlocal.feature.webclipper.presentation.images

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.feature.webclipper.domain.ClipSession
import com.llmlocal.feature.webclipper.domain.IMAGES_CANDIDATE_ID
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.feature.webclipper.domain.input.ClipImage
import com.llmlocal.feature.webclipper.domain.input.ClipInput
import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability
import com.llmlocal.feature.webclipper.domain.usecase.DiscardPreparedImagesUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ObserveInputCapabilitiesUseCase
import com.llmlocal.feature.webclipper.domain.usecase.PrepareImagesUseCase
import com.llmlocal.feature.webclipper.presentation.WebClipperLabels
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Prepare/review screen of the images source (UX spec §2.3–§2.4).
 *
 * Where the pictures come from, in order: the prepared paths kept in the
 * [SavedStateHandle] (process death), a `ClipInput.Images` left in the
 * [ClipSession] ("Pick different pictures" from the extraction screen), the
 * [pendingUris] of a share, else the system picker is launched. A batch that
 * prepares to exactly one picture goes straight to extraction; two or more
 * wait for the user to confirm the order.
 *
 * The prepared files belong to this ViewModel until [ImagesClipIntent.Extract]
 * hands them to the session; [ImagesClipIntent.Close] (and a pop) deletes them.
 */
class ImagesClipViewModel(
    private val pendingUris: List<String>?,
    private val savedStateHandle: SavedStateHandle,
    private val config: WebClipConfig<*>,
    private val labels: WebClipperLabels,
    private val session: ClipSession,
    private val prepareImages: PrepareImagesUseCase,
    private val discardImages: DiscardPreparedImagesUseCase,
    private val observeCapabilities: ObserveInputCapabilitiesUseCase,
    @Suppress("unused") private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val policy get() = config.imagePolicy

    private val _state = MutableStateFlow(ImagesClipState(maxImages = policy.maxImages))
    val state: StateFlow<ImagesClipState> = _state.asStateFlow()

    private val _effects = Channel<ImagesClipEffect>(Channel.BUFFERED)
    val effects: Flow<ImagesClipEffect> = _effects.receiveAsFlow()

    private var prepareJob: Job? = null

    /** The pictures went to the session (or were discarded): nothing left to clean up. */
    private var released = false

    init {
        observeCapabilities.observe(viewModelScope)
            .onEach { availability -> _state.update { it.copy(availability = availability) } }
            .launchIn(viewModelScope)

        val restored = restoreFromSavedState()
        val returned = (session.current.value?.input as? ClipInput.Images)?.images?.filter { it.file.exists() }
        when {
            restored.isNotEmpty() -> setImages(restored)
            !returned.isNullOrEmpty() -> {
                session.clearPage()
                setImages(returned)
            }
            pendingUris != null -> onPicked(pendingUris)
            else -> send(ImagesClipEffect.LaunchPicker)
        }
    }

    fun onIntent(intent: ImagesClipIntent) {
        when (intent) {
            is ImagesClipIntent.ImagesPicked -> onPicked(intent.uris)
            ImagesClipIntent.PickerCancelled -> if (_state.value.images.isEmpty() && !_state.value.isPreparing) close()
            is ImagesClipIntent.Select -> _state.update { it.copy(selectedKey = intent.key) }
            is ImagesClipIntent.Move -> move(intent.from, intent.to)
            is ImagesClipIntent.Remove -> remove(intent.key)
            ImagesClipIntent.AddMore -> addMore()
            ImagesClipIntent.Extract -> extract()
            ImagesClipIntent.Close -> close()
            ImagesClipIntent.OpenModelManagement -> send(ImagesClipEffect.NavigateToModelManagement)
        }
    }

    // -- preparing -------------------------------------------------------------

    private fun onPicked(uris: List<String>) {
        if (uris.isEmpty()) {
            if (_state.value.images.isEmpty()) close()
            return
        }
        val before = _state.value.images
        val room = (policy.maxImages - before.size).coerceAtLeast(0)
        if (room == 0) return
        _state.update { it.copy(isPreparing = true, skipped = 0, capped = false) }
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch {
            val result = prepareImages(uris, policy.copy(maxImages = room))
            val images = before + result.images
            _state.update {
                it.copy(
                    images = images,
                    selectedKey = it.selectedKey?.takeIf { key -> images.any { image -> image.key == key } }
                        ?: images.firstOrNull()?.key,
                    isPreparing = false,
                    skipped = result.skipped,
                    capped = result.capped,
                )
            }
            persist()
            if (result.skipped > 0) send(ImagesClipEffect.Notify(ImagesNotice.SKIPPED))
            if (result.capped) send(ImagesClipEffect.Notify(ImagesNotice.CAPPED))
            if (before.isEmpty() && images.size == 1 && _state.value.canExtract) extract()
        }
    }

    private fun setImages(images: List<ClipImage>) {
        _state.update { it.copy(images = images, selectedKey = images.first().key, isPreparing = false) }
        persist()
    }

    // -- review ----------------------------------------------------------------

    private fun move(from: Int, to: Int) {
        val images = _state.value.images
        if (from !in images.indices || to !in images.indices || from == to) return
        val reordered = images.toMutableList().apply { add(to, removeAt(from)) }
        _state.update { it.copy(images = reordered, selectedKey = reordered[to].key) }
        persist()
    }

    private fun remove(key: String) {
        val images = _state.value.images
        val index = images.indexOfFirst { it.key == key }
        if (index < 0) return
        discardImages(listOf(images[index]))
        val remaining = images - images[index]
        _state.update {
            it.copy(
                images = remaining,
                selectedKey = if (it.selectedKey == key) remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.key else it.selectedKey,
                capped = false,
            )
        }
        persist()
    }

    private fun addMore() {
        val remaining = policy.maxImages - _state.value.images.size
        if (remaining > 0) send(ImagesClipEffect.LaunchPickerFor(remaining))
    }

    private fun extract() {
        val current = _state.value
        if (!current.canExtract || current.isPreparing) return
        val input = ClipInput.Images(current.images)
        session.set(input, config.candidatesFor(input, labels.imagesTitle))
        released = true
        savedStateHandle.remove<List<String>>(SAVED_KEY)
        send(ImagesClipEffect.NavigateToExtraction(IMAGES_CANDIDATE_ID))
    }

    private fun close() {
        prepareJob?.cancel()
        discardAll()
        send(ImagesClipEffect.Closed)
    }

    private fun discardAll() {
        if (released) return
        released = true
        discardImages(_state.value.images)
        savedStateHandle.remove<List<String>>(SAVED_KEY)
    }

    override fun onCleared() {
        prepareJob?.cancel()
        discardAll()
    }

    // -- state restore -----------------------------------------------------------

    /** Prepared pictures survive process death as `file|width|height|sha|tileOf` strings. */
    private fun persist() {
        savedStateHandle[SAVED_KEY] = ArrayList(_state.value.images.map(::encode))
    }

    private fun restoreFromSavedState(): List<ClipImage> =
        savedStateHandle.get<List<String>>(SAVED_KEY).orEmpty()
            .mapNotNull(::decode)
            .filter { it.file.exists() }

    private fun send(effect: ImagesClipEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    companion object {
        /** `SavedStateHandle` key of the prepared pictures. */
        const val SAVED_KEY = "images.prepared"

        internal fun encode(image: ClipImage): String =
            listOf(image.file.path, image.width, image.height, image.sha256, image.tileOf ?: "").joinToString("|")

        internal fun decode(encoded: String): ClipImage? {
            val parts = encoded.split("|")
            if (parts.size != 5) return null
            return ClipImage(
                file = File(parts[0]),
                width = parts[1].toIntOrNull() ?: return null,
                height = parts[2].toIntOrNull() ?: return null,
                sha256 = parts[3],
                tileOf = parts[4].toIntOrNull(),
            )
        }
    }
}

/** Stable identity of a prepared picture (its temp file name is unique per run). */
val ClipImage.key: String get() = file.name
