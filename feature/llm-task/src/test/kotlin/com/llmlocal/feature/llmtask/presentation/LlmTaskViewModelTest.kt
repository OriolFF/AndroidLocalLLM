package com.llmlocal.feature.llmtask.presentation

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.task.LlmTaskConfig
import com.llmlocal.core.domain.task.PromptBuilder
import com.llmlocal.core.domain.task.TaskParser
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.feature.llmtask.domain.LlmGenerationSource
import com.llmlocal.feature.llmtask.domain.ModelAvailability
import com.llmlocal.feature.llmtask.domain.ModelReadinessSource
import com.llmlocal.feature.llmtask.domain.SelectedModelSource
import com.llmlocal.feature.llmtask.domain.usecase.CancelGenerationUseCase
import com.llmlocal.feature.llmtask.domain.usecase.GenerateTaskUseCase
import com.llmlocal.feature.llmtask.domain.usecase.ObserveModelReadinessUseCase
import com.llmlocal.feature.llmtask.domain.usecase.ObserveSelectedModelUseCase
import com.llmlocal.feature.llmtask.domain.usecase.RecheckModelUseCase
import com.llmlocal.feature.llmtask.presentation.mvi.LlmTaskIntent
import com.llmlocal.feature.llmtask.presentation.mvi.LlmTaskLabels
import com.llmlocal.feature.llmtask.presentation.mvi.ModelStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LlmTaskViewModelTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
    }

    private val availability = MutableStateFlow<ModelAvailability>(ModelAvailability.Checking)
    private val readiness = FakeReadinessSource(availability)
    private val selection = FakeSelectedModelSource()
    private val generation = FakeGenerationSource()
    private val config = LlmTaskConfig<String>(
        promptBuilder = object : PromptBuilder {
            override fun systemPrompt() = "sys"
            override fun userPrompt(inputs: List<String>) = inputs.joinToString()
        },
        parser = TaskParser { it },
    )
    private val labels = TestLlmTaskLabels

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = LlmTaskViewModel(
        observeReadiness = ObserveModelReadinessUseCase(readiness),
        observeSelectedModel = ObserveSelectedModelUseCase(selection),
        recheckModel = RecheckModelUseCase(readiness),
        generateTask = GenerateTaskUseCase(generation),
        cancelGeneration = CancelGenerationUseCase(generation),
        dispatchers = dispatchers,
        config = config,
        labels = labels,
    )

    private fun LlmTaskViewModel.addInput(text: String) {
        onIntent(LlmTaskIntent.UpdateInput(text))
        onIntent(LlmTaskIntent.AddInput)
    }

    @Test
    fun `ready model with inputs enables generate`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.addInput("egg")
        advanceUntilIdle()
        assertThat(vm.state.value.canGenerate).isFalse()

        availability.value = ModelAvailability.Ready
        advanceUntilIdle()

        assertThat(vm.state.value.modelStatus).isEqualTo(ModelStatus.Ready)
        assertThat(vm.state.value.selectedModelInstalled).isTrue()
        assertThat(vm.state.value.canGenerate).isTrue()
    }

    @Test
    fun `downloading maps to no model available and failed keeps reason`() = runTest(dispatcher) {
        val vm = viewModel()
        availability.value = ModelAvailability.Downloading
        advanceUntilIdle()
        assertThat(vm.state.value.modelStatus).isEqualTo(ModelStatus.NoModelAvailable)

        availability.value = ModelAvailability.Failed("boom", installed = true)
        advanceUntilIdle()
        assertThat(vm.state.value.modelStatus).isEqualTo(ModelStatus.Failed("boom"))
        assertThat(vm.state.value.selectedModelInstalled).isTrue()
    }

    @Test
    fun `selected model flows into state`() = runTest(dispatcher) {
        val vm = viewModel()
        selection.ids.value = "m1"
        advanceUntilIdle()
        assertThat(vm.state.value.selectedModel?.id).isEqualTo("m1")
    }

    @Test
    fun `generation events update streamed text then result`() = runTest(dispatcher) {
        availability.value = ModelAvailability.Ready
        val vm = viewModel()
        vm.addInput("egg")
        advanceUntilIdle()

        vm.onIntent(LlmTaskIntent.Generate)
        advanceUntilIdle()
        assertThat(vm.state.value.isGenerating).isTrue()
        assertThat(generation.lastInputs).containsExactly("egg")

        generation.events.emit(LlmTaskEvent.Token("ab"))
        generation.events.emit(LlmTaskEvent.Token("cd"))
        advanceUntilIdle()
        assertThat(vm.state.value.streamedText).isEqualTo("abcd")
        assertThat(vm.state.value.result).isNull()

        generation.events.emit(LlmTaskEvent.Complete("abcd"))
        advanceUntilIdle()
        assertThat(vm.state.value.result).isEqualTo("abcd")
        assertThat(vm.state.value.isGenerating).isFalse()
    }

    @Test
    fun `failed event surfaces error message`() = runTest(dispatcher) {
        availability.value = ModelAvailability.Ready
        val vm = viewModel()
        vm.addInput("egg")
        advanceUntilIdle()
        vm.onIntent(LlmTaskIntent.Generate)
        advanceUntilIdle()

        generation.events.emit(LlmTaskEvent.Failed(IllegalStateException("bad")))
        advanceUntilIdle()

        assertThat(vm.state.value.errorMessage).isEqualTo("bad")
        assertThat(vm.state.value.isGenerating).isFalse()
    }

    @Test
    fun `cancel stops generation and resets flag`() = runTest(dispatcher) {
        availability.value = ModelAvailability.Ready
        val vm = viewModel()
        vm.addInput("egg")
        advanceUntilIdle()
        vm.onIntent(LlmTaskIntent.Generate)
        advanceUntilIdle()
        assertThat(vm.state.value.isGenerating).isTrue()

        vm.onIntent(LlmTaskIntent.CancelGeneration)
        advanceUntilIdle()

        assertThat(generation.cancelCount).isEqualTo(1)
        assertThat(vm.state.value.isGenerating).isFalse()
    }

    @Test
    fun `check model intent rechecks readiness`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onIntent(LlmTaskIntent.CheckModel)
        advanceUntilIdle()
        assertThat(readiness.rechecks).isEqualTo(1)
    }
}

private class FakeReadinessSource(private val flow: StateFlow<ModelAvailability>) : ModelReadinessSource {
    var rechecks = 0
    override fun observe(scope: CoroutineScope): StateFlow<ModelAvailability> = flow
    override suspend fun recheck() { rechecks++ }
}

private class FakeSelectedModelSource : SelectedModelSource {
    val ids = MutableStateFlow<String?>(null)
    override val selectedModelId: Flow<String?> = ids
    override fun current(): String = ids.value ?: "default"
    override fun describe(modelId: String): LlmModelDescriptor =
        LlmModelDescriptor(id = modelId, displayName = modelId, description = "", url = "", sizeBytes = 1, filename = "$modelId.bin")
}

private class FakeGenerationSource : LlmGenerationSource {
    val events = MutableSharedFlow<LlmTaskEvent<*>>()
    var lastInputs: List<String> = emptyList()
    var cancelCount = 0

    @Suppress("UNCHECKED_CAST")
    override fun <T> generate(config: LlmTaskConfig<T>, inputs: List<String>): Flow<LlmTaskEvent<T>> {
        lastInputs = inputs
        return events as Flow<LlmTaskEvent<T>>
    }

    override fun cancel() { cancelCount++ }
}
