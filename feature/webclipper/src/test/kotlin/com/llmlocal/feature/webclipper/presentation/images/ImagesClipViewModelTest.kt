package com.llmlocal.feature.webclipper.presentation.images

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.core.llm.readiness.ModelReadiness
import com.llmlocal.core.model.ModelCapability
import com.llmlocal.feature.webclipper.data.CacheDirTempPictureStore
import com.llmlocal.feature.webclipper.data.InMemoryClipSession
import com.llmlocal.feature.webclipper.domain.ClipSession
import com.llmlocal.feature.webclipper.domain.IMAGES_CANDIDATE_ID
import com.llmlocal.feature.webclipper.domain.ImagePolicy
import com.llmlocal.feature.webclipper.domain.input.ClipInput
import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability
import com.llmlocal.feature.webclipper.domain.usecase.DiscardPreparedImagesUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ObserveInputCapabilitiesUseCase
import com.llmlocal.feature.webclipper.domain.usecase.PrepareImagesUseCase
import com.llmlocal.feature.webclipper.fakes.FakeLocalImageImporter
import com.llmlocal.feature.webclipper.fakes.FakeReadinessSource
import com.llmlocal.feature.webclipper.fakes.FakeWebClipConfig
import com.llmlocal.feature.webclipper.fakes.MainDispatcherRule
import com.llmlocal.feature.webclipper.fakes.TestDispatchers
import com.llmlocal.feature.webclipper.fakes.preparedImage
import com.llmlocal.feature.webclipper.presentation.testWebClipperLabels
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ImagesClipViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val dir get() = File(temp.root, "webclipper")
    private val labels = testWebClipperLabels()
    private val session: ClipSession = InMemoryClipSession()
    private val visionReady = ModelReadiness.Ready(
        LlmModelCatalog.DEFAULT_MODEL.copy(capabilities = setOf(ModelCapability.TEXT, ModelCapability.VISION)),
    )
    private val readiness = FakeReadinessSource(visionReady)
    private val config = FakeWebClipConfig(imagePolicy = ImagePolicy(enabled = true, maxImages = 3, tileImages = false))

    private fun viewModel(
        pending: List<String>? = null,
        handle: SavedStateHandle = SavedStateHandle(),
        importer: FakeLocalImageImporter = FakeLocalImageImporter(),
    ): ImagesClipViewModel {
        val dispatchers = TestDispatchers(mainDispatcherRule.dispatcher)
        val store = CacheDirTempPictureStore(dir)
        return ImagesClipViewModel(
            pendingUris = pending,
            savedStateHandle = handle,
            config = config,
            labels = labels,
            session = session,
            prepareImages = PrepareImagesUseCase(importer, store, dispatchers),
            discardImages = DiscardPreparedImagesUseCase(store),
            observeCapabilities = ObserveInputCapabilitiesUseCase(readiness, config),
            dispatchers = dispatchers,
        )
    }

    private fun files() = dir.listFiles()?.toList().orEmpty()

    @Test
    fun `nothing pending launches the picker`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            assertThat(awaitItem()).isEqualTo(ImagesClipEffect.LaunchPicker)
        }
    }

    @Test
    fun `shared pictures are prepared and two or more wait in the review`() = runTest {
        val vm = viewModel(pending = listOf("a", "b"))
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.isPreparing).isFalse()
        assertThat(state.images).hasSize(2)
        assertThat(state.needsReview).isTrue()
        assertThat(state.selectedKey).isEqualTo(state.images.first().key)
        assertThat(state.canExtract).isTrue()
        assertThat(session.current.value).isNull()
        vm.effects.test { expectNoEvents() }
    }

    @Test
    fun `a picker batch that ends in exactly one picture goes straight to extraction`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.effects.test {
            assertThat(awaitItem()).isEqualTo(ImagesClipEffect.LaunchPicker)
            vm.onIntent(ImagesClipIntent.ImagesPicked(listOf("a")))
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(ImagesClipEffect.NavigateToExtraction(IMAGES_CANDIDATE_ID))
            val current = session.current.value!!
            assertThat((current.input as ClipInput.Images).images).hasSize(1)
            assertThat(current.candidates.single().id).isEqualTo(IMAGES_CANDIDATE_ID)
            assertThat(current.candidates.single().title).isEqualTo(labels.imagesTitle)
        }
    }

    @Test
    fun `without a model nothing is auto-extracted and Extract stays disabled`() = runTest {
        readiness.readiness.value = ModelReadiness.NoModel
        val vm = viewModel(pending = listOf("a"))
        advanceUntilIdle()

        assertThat(vm.state.value.availability).isEqualTo(ImagesAvailability.NoModel)
        assertThat(vm.state.value.canExtract).isFalse()
        vm.onIntent(ImagesClipIntent.Extract)
        advanceUntilIdle()
        assertThat(session.current.value).isNull()
        vm.effects.test { expectNoEvents() }
    }

    @Test
    fun `a text-only model reports the lack of vision and blocks extraction`() = runTest {
        readiness.readiness.value = ModelReadiness.Ready(LlmModelCatalog.DEFAULT_MODEL.copy(capabilities = setOf(ModelCapability.TEXT)))
        val vm = viewModel(pending = listOf("a", "b"))
        advanceUntilIdle()

        assertThat(vm.state.value.availability).isEqualTo(ImagesAvailability.ModelLacksVision)
        assertThat(vm.state.value.canExtract).isFalse()
    }

    @Test
    fun `move, select and remove are pure state`() = runTest {
        val vm = viewModel(pending = listOf("a", "b", "c"))
        advanceUntilIdle()
        val (a, b, c) = vm.state.value.images.map { it.key }

        vm.onIntent(ImagesClipIntent.Move(from = 0, to = 2))
        assertThat(vm.state.value.images.map { it.key }).containsExactly(b, c, a).inOrder()
        assertThat(vm.state.value.selectedKey).isEqualTo(a) // the moved tile stays selected

        vm.onIntent(ImagesClipIntent.Move(from = 5, to = 0)) // out of range: ignored
        assertThat(vm.state.value.images.map { it.key }).containsExactly(b, c, a).inOrder()

        vm.onIntent(ImagesClipIntent.Select(c))
        assertThat(vm.state.value.selectedKey).isEqualTo(c)

        vm.onIntent(ImagesClipIntent.Remove(c))
        assertThat(vm.state.value.images.map { it.key }).containsExactly(b, a).inOrder()
        assertThat(vm.state.value.selectedKey).isNotEqualTo(c)
        assertThat(files()).hasSize(2) // the removed picture's file is gone
    }

    @Test
    fun `Extract hands the pictures in their new order to the session and navigates`() = runTest {
        val vm = viewModel(pending = listOf("a", "b"))
        advanceUntilIdle()
        val (a, b) = vm.state.value.images
        vm.onIntent(ImagesClipIntent.Move(0, 1))

        vm.effects.test {
            vm.onIntent(ImagesClipIntent.Extract)
            assertThat(awaitItem()).isEqualTo(ImagesClipEffect.NavigateToExtraction(IMAGES_CANDIDATE_ID))
        }

        assertThat((session.current.value!!.input as ClipInput.Images).images).containsExactly(b, a).inOrder()
        // the pictures now belong to the extraction: a later Close must not delete them
        vm.onIntent(ImagesClipIntent.Close)
        advanceUntilIdle()
        assertThat(files()).hasSize(2)
    }

    @Test
    fun `Close deletes the prepared pictures and leaves`() = runTest {
        val vm = viewModel(pending = listOf("a", "b"))
        advanceUntilIdle()
        assertThat(files()).hasSize(2)

        vm.effects.test {
            vm.onIntent(ImagesClipIntent.Close)
            assertThat(awaitItem()).isEqualTo(ImagesClipEffect.Closed)
        }
        assertThat(files()).isEmpty()
    }

    @Test
    fun `a cancelled first pick closes silently, a cancelled add-more keeps the review`() = runTest {
        val empty = viewModel()
        empty.effects.test {
            assertThat(awaitItem()).isEqualTo(ImagesClipEffect.LaunchPicker)
            empty.onIntent(ImagesClipIntent.PickerCancelled)
            assertThat(awaitItem()).isEqualTo(ImagesClipEffect.Closed)
        }

        val reviewing = viewModel(pending = listOf("a", "b"))
        advanceUntilIdle()
        reviewing.onIntent(ImagesClipIntent.PickerCancelled)
        advanceUntilIdle()
        assertThat(reviewing.state.value.images).hasSize(2)
        reviewing.effects.test { expectNoEvents() }
    }

    @Test
    fun `add more launches the picker for the remaining slots and appends the new pictures`() = runTest {
        val vm = viewModel(pending = listOf("a", "b"))
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(ImagesClipIntent.AddMore)
            assertThat(awaitItem()).isEqualTo(ImagesClipEffect.LaunchPickerFor(1))
            vm.onIntent(ImagesClipIntent.ImagesPicked(listOf("c", "d")))
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ImagesClipEffect.Notify(ImagesNotice.CAPPED))
        }

        assertThat(vm.state.value.images).hasSize(3)
        assertThat(vm.state.value.capped).isTrue()
        // at the cap there is nothing left to add
        vm.effects.test {
            vm.onIntent(ImagesClipIntent.AddMore)
            expectNoEvents()
        }
    }

    @Test
    fun `unreadable pictures are reported through a notice and the count`() = runTest {
        val vm = viewModel(pending = listOf("a", "bad", "c"), importer = FakeLocalImageImporter(failing = setOf("bad")))
        vm.effects.test {
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(ImagesClipEffect.Notify(ImagesNotice.SKIPPED))
        }
        assertThat(vm.state.value.skipped).isEqualTo(1)
        assertThat(vm.state.value.images).hasSize(2)
    }

    @Test
    fun `nothing readable leaves an empty review with the skip count`() = runTest {
        val vm = viewModel(pending = listOf("bad"), importer = FakeLocalImageImporter(failing = setOf("bad")))
        advanceUntilIdle()

        assertThat(vm.state.value.images).isEmpty()
        assertThat(vm.state.value.skipped).isEqualTo(1)
        assertThat(vm.state.value.canExtract).isFalse()
    }

    @Test
    fun `prepared pictures survive through the SavedStateHandle`() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(pending = listOf("a", "b"), handle = handle)
        advanceUntilIdle()
        val prepared = first.state.value.images

        val restored = viewModel(handle = handle) // new process: no pending uris
        advanceUntilIdle()

        assertThat(restored.state.value.images).isEqualTo(prepared)
        restored.effects.test { expectNoEvents() } // no picker either
    }

    @Test
    fun `pictures left in the session by Pick different are taken back for the review`() = runTest {
        val pictures = listOf(preparedImage(dir, 0), preparedImage(dir, 1))
        session.set(ClipInput.Images(pictures), emptyList())

        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.images).isEqualTo(pictures)
        assertThat(session.current.value).isNull()
        vm.effects.test { expectNoEvents() }
    }

    @Test
    fun `restoring ignores files that no longer exist`() {
        val handle = SavedStateHandle()
        val gone = preparedImage(dir, 0)
        handle[ImagesClipViewModel.SAVED_KEY] = arrayListOf(ImagesClipViewModel.encode(gone))
        gone.file.delete()

        val vm = viewModel(handle = handle)

        assertThat(vm.state.value.images).isEmpty()
    }

    @Test
    fun `encode and decode round-trip a tile`() {
        val image = preparedImage(dir, 3).copy(tileOf = 2)
        assertThat(ImagesClipViewModel.decode(ImagesClipViewModel.encode(image))).isEqualTo(image)
        assertThat(ImagesClipViewModel.decode("garbage")).isNull()
    }

    @Test
    fun `OpenModelManagement navigates`() = runTest {
        val vm = viewModel(pending = listOf("a", "b"))
        advanceUntilIdle()
        vm.effects.test {
            vm.onIntent(ImagesClipIntent.OpenModelManagement)
            assertThat(awaitItem()).isEqualTo(ImagesClipEffect.NavigateToModelManagement)
        }
    }
}
