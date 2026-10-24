package com.llmlocal.feature.webclipper.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.core.llm.readiness.ModelReadiness
import com.llmlocal.core.model.ModelCapability
import com.llmlocal.feature.webclipper.domain.ImagePolicy
import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability
import com.llmlocal.feature.webclipper.fakes.FakeReadinessSource
import com.llmlocal.feature.webclipper.fakes.FakeWebClipConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveInputCapabilitiesUseCaseTest {

    private val visionModel = LlmModelCatalog.DEFAULT_MODEL.copy(capabilities = setOf(ModelCapability.TEXT, ModelCapability.VISION))
    private val textModel = LlmModelCatalog.DEFAULT_MODEL.copy(capabilities = setOf(ModelCapability.TEXT))

    private fun availability(enabled: Boolean, readiness: ModelReadiness): ImagesAvailability {
        val source = FakeReadinessSource(readiness)
        val config = FakeWebClipConfig(imagePolicy = ImagePolicy(enabled = enabled))
        var result = ImagesAvailability.Disabled
        runTest(UnconfinedTestDispatcher()) {
            result = ObserveInputCapabilitiesUseCase(source, config).observe(backgroundScope).value
        }
        return result
    }

    @Test
    fun `a model that reads images makes the source available`() {
        assertThat(availability(true, ModelReadiness.Ready(visionModel))).isEqualTo(ImagesAvailability.Available)
    }

    @Test
    fun `a text-only model is reported as lacking vision`() {
        assertThat(availability(true, ModelReadiness.Ready(textModel))).isEqualTo(ImagesAvailability.ModelLacksVision)
    }

    @Test
    fun `every not-ready state reads as no model`() {
        val downloading = ModelReadiness.Downloading(PerModelProgress("m", DownloadState.RUNNING, 0L, null, 0L, null, 0L))
        listOf(
            ModelReadiness.Checking,
            ModelReadiness.NoModel,
            downloading,
            ModelReadiness.Initializing,
            ModelReadiness.Failed("boom", visionModel),
        ).forEach { assertThat(availability(true, it)).isEqualTo(ImagesAvailability.NoModel) }
    }

    @Test
    fun `a disabled policy wins over any readiness`() {
        assertThat(availability(false, ModelReadiness.Ready(visionModel))).isEqualTo(ImagesAvailability.Disabled)
        assertThat(availability(false, ModelReadiness.NoModel)).isEqualTo(ImagesAvailability.Disabled)
    }

    @Test
    fun `availability follows the readiness stream`() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeReadinessSource(ModelReadiness.NoModel)
        val flow = ObserveInputCapabilitiesUseCase(source, FakeWebClipConfig()).observe(backgroundScope)
        assertThat(flow.value).isEqualTo(ImagesAvailability.NoModel)

        source.readiness.value = ModelReadiness.Ready(visionModel)

        assertThat(flow.value).isEqualTo(ImagesAvailability.Available)
    }
}
