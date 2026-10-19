package com.llmlocal.feature.modelmanagement.presentation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.feature.modelmanagement.FakeCatalog
import com.llmlocal.feature.modelmanagement.FakeInstalled
import com.llmlocal.feature.modelmanagement.FakeProgress
import com.llmlocal.feature.modelmanagement.FakeScheduler
import com.llmlocal.feature.modelmanagement.FakeSelection
import com.llmlocal.feature.modelmanagement.FakeWork
import com.llmlocal.feature.modelmanagement.MainDispatcherRule
import com.llmlocal.feature.modelmanagement.TestDispatchers
import com.llmlocal.feature.modelmanagement.TestLabels
import com.llmlocal.feature.modelmanagement.descriptor
import com.llmlocal.feature.modelmanagement.domain.DownloadWorkStatus
import com.llmlocal.feature.modelmanagement.domain.ModelFilters
import com.llmlocal.feature.modelmanagement.domain.StatusFilter
import com.llmlocal.feature.modelmanagement.domain.usecase.CancelDownloadUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.DownloadModelUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.FilterModelsUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.ObserveModelListingsUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.RemoveModelUseCase
import com.llmlocal.feature.modelmanagement.domain.usecase.SelectModelUseCase
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementEffect
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementIntent
import com.llmlocal.feature.modelmanagement.progress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelManagementViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val catalog = FakeCatalog(listOf(descriptor("a"), descriptor("b")))
    private val installed = FakeInstalled(setOf("a"))
    private val selection = FakeSelection("a")
    private val progressSource = FakeProgress()
    private val scheduler = FakeScheduler()
    private val work = FakeWork()

    private fun viewModel() = ModelManagementViewModel(
        observeListings = ObserveModelListingsUseCase(catalog, installed, selection, progressSource, work),
        downloadModel = DownloadModelUseCase(catalog, scheduler),
        cancelDownload = CancelDownloadUseCase(scheduler),
        selectModel = SelectModelUseCase(catalog, installed, selection),
        removeModel = RemoveModelUseCase(catalog, installed),
        filterModels = FilterModelsUseCase(),
        dispatchers = TestDispatchers(mainDispatcherRule.dispatcher),
        labels = TestLabels,
    )

    @Test
    fun `listing combines catalog installed selection and progress`() = runTest {
        val vm = viewModel()
        progressSource.flow.value = mapOf("b" to progress("b"))
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.catalog.map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(state.installedIds).containsExactly("a")
        assertThat(state.selectedId).isEqualTo("a")
        assertThat(state.downloading.keys).containsExactly("b")
        assertThat(state.filteredCatalog).hasSize(2)
    }

    @Test
    fun `status filter narrows filteredCatalog`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(ModelManagementIntent.SetFilters(ModelFilters(status = StatusFilter.INSTALLED)))
        advanceUntilIdle()
        assertThat(vm.state.value.filteredCatalog.map { it.id }).containsExactly("a")

        vm.onIntent(ModelManagementIntent.ClearFilters)
        advanceUntilIdle()
        assertThat(vm.state.value.filteredCatalog).hasSize(2)
    }

    @Test
    fun `selecting a model that is not installed shows the not downloaded snackbar`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.effects.test {
            vm.onIntent(ModelManagementIntent.Select("b"))
            assertThat(awaitItem()).isEqualTo(ModelManagementEffect.ShowSnackbar("not downloaded Model b"))
        }
        assertThat(vm.state.value.selectedId).isEqualTo("a")
    }

    @Test
    fun `selecting an installed model updates selection and snackbar`() = runTest {
        installed.ids += "b"
        val vm = viewModel()
        advanceUntilIdle()
        vm.effects.test {
            vm.onIntent(ModelManagementIntent.Select("b"))
            assertThat(awaitItem()).isEqualTo(ModelManagementEffect.ShowSnackbar("selected Model b"))
        }
        advanceUntilIdle()
        assertThat(vm.state.value.selectedId).isEqualTo("b")
    }

    @Test
    fun `removing a model emits snackbar and refreshes installed set`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.effects.test {
            vm.onIntent(ModelManagementIntent.Remove("a"))
            assertThat(awaitItem()).isEqualTo(ModelManagementEffect.ShowSnackbar("removed Model a"))
        }
        advanceUntilIdle()
        assertThat(vm.state.value.installedIds).isEmpty()
    }

    @Test
    fun `download and cancel go to the scheduler and unknown id sets lastError`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(ModelManagementIntent.StartDownload("b"))
        vm.onIntent(ModelManagementIntent.CancelDownload("b"))
        vm.onIntent(ModelManagementIntent.StartDownload("zzz"))
        advanceUntilIdle()
        assertThat(scheduler.enqueued).containsExactly("b")
        assertThat(scheduler.cancelled).containsExactly("b")
        assertThat(vm.state.value.lastError).isEqualTo("Unknown model: zzz")
        vm.onIntent(ModelManagementIntent.DismissError)
        advanceUntilIdle()
        assertThat(vm.state.value.lastError).isNull()
    }

    @Test
    fun `a succeeded download job rescans the installed set`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.installedIds).containsExactly("a")

        installed.ids += "b"
        work.flow.value = listOf(DownloadWorkStatus.SUCCEEDED)
        advanceUntilIdle()
        assertThat(vm.state.value.installedIds).containsExactly("a", "b")
    }
}
