package com.llmlocal.feature.webclipper

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.core.model.ModelFamily
import com.llmlocal.feature.webclipper.fakes.FakePageScanner
import com.llmlocal.feature.webclipper.fakes.FakeReadinessSource
import com.llmlocal.feature.webclipper.fakes.FakeWebClipConfig
import com.llmlocal.feature.webclipper.fakes.MainDispatcherRule
import com.llmlocal.feature.webclipper.fakes.TestDispatchers
import com.llmlocal.feature.webclipper.fakes.candidate
import com.llmlocal.feature.webclipper.mvi.BrowserEffect
import com.llmlocal.feature.webclipper.mvi.BrowserIntent
import com.llmlocal.feature.webclipper.mvi.BrowserSheet
import com.llmlocal.feature.webclipper.mvi.ClipFabState
import com.llmlocal.feature.webclipper.readiness.ModelReadiness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val descriptor = LlmModelDescriptor(
        id = "m", displayName = "M", description = "", url = "https://x/m", sizeBytes = 1, filename = "m.litertlm",
        tags = emptyList(), license = "", author = "", minRamMb = 1, family = ModelFamily.INSTRUCT,
    )
    private val labels = WebClipperLabels(searchUrlTemplate = { q -> "https://search.example/?q=$q" })
    private val session = ClipSession()
    private val readiness = FakeReadinessSource(ModelReadiness.Ready(descriptor))
    private val scanner = FakePageScanner()
    private val config = FakeWebClipConfig()

    private fun viewModel(initialUrl: String? = null) = BrowserViewModel(
        config = config,
        labels = labels,
        scanner = scanner,
        session = session,
        readinessSource = readiness,
        dispatchers = TestDispatchers(mainDispatcherRule.dispatcher),
        initialUrl = initialUrl,
    )

    private fun BrowserViewModel.loadPage(url: String = "https://www.cooking.example/recipes/pasta") {
        onIntent(BrowserIntent.PageStarted(url))
        onIntent(BrowserIntent.PageProgress(100))
        onIntent(BrowserIntent.PageFinished(url, "Pasta"))
    }

    @Test
    fun `single candidate skips the sheet and navigates to extraction`() = runTest {
        config.candidates = listOf(candidate("only"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadPage()

        vm.effects.test {
            vm.onIntent(BrowserIntent.Scan)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.NavigateToExtraction("only"))
        }
        assertThat(vm.state.value.sheet).isEqualTo(BrowserSheet.None)
        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.Found(1))
        assertThat(session.current.value?.candidates?.map { it.id }).containsExactly("only")
        assertThat(scanner.captures).isEqualTo(1)
    }

    @Test
    fun `multiple candidates open the candidate sheet`() = runTest {
        config.candidates = listOf(candidate("a"), candidate("b"), candidate("c"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadPage()

        vm.onIntent(BrowserIntent.Scan)
        advanceUntilIdle()

        assertThat(vm.state.value.sheet).isEqualTo(BrowserSheet.Candidates)
        assertThat(vm.state.value.candidates).hasSize(3)
        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.Found(3))

        vm.effects.test {
            vm.onIntent(BrowserIntent.PickCandidate("b"))
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.NavigateToExtraction("b"))
        }
        assertThat(vm.state.value.sheet).isEqualTo(BrowserSheet.None)
    }

    @Test
    fun `model not ready blocks the scan and navigates to model management`() = runTest {
        readiness.readiness.value = ModelReadiness.NoModel
        config.candidates = listOf(candidate("a"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadPage()

        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.ModelNotReady())
        vm.effects.test {
            vm.onIntent(BrowserIntent.Scan)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.ShowSnackbar(labels.modelNotReadySnackbar))
            assertThat(awaitItem()).isEqualTo(BrowserEffect.NavigateToModelManagement)
        }
        assertThat(scanner.captures).isEqualTo(0)
    }

    @Test
    fun `nothing detected opens the sheet and extract anyway navigates with the whole page`() = runTest {
        config.candidates = emptyList()
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadPage()

        vm.onIntent(BrowserIntent.Scan)
        advanceUntilIdle()
        assertThat(vm.state.value.sheet).isEqualTo(BrowserSheet.NothingDetected)

        vm.effects.test {
            vm.onIntent(BrowserIntent.ExtractWholePage)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.NavigateToExtraction(WHOLE_PAGE_CANDIDATE_ID))
        }
        assertThat(session.candidate(WHOLE_PAGE_CANDIDATE_ID)?.payload).isEqualTo(CandidatePayload.WholePage)
        assertThat(scanner.captures).isEqualTo(1)
    }

    @Test
    fun `capture failure shows a snackbar and resets the button`() = runTest {
        scanner.captureOutcome = Outcome.Failure(IllegalStateException("js"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadPage()

        vm.effects.test {
            vm.onIntent(BrowserIntent.Scan)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.ShowSnackbar(labels.scanFailed))
        }
        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.Idle)
    }

    @Test
    fun `proactive quick scan shows Found after the debounce without a tap`() = runTest {
        scanner.quickCount = 2
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadPage()

        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.Idle)
        advanceTimeBy(BrowserViewModel.QUICK_SCAN_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.Found(2))
        assertThat(scanner.captures).isEqualTo(0)
        assertThat(vm.state.value.candidates).isEmpty()
    }

    @Test
    fun `page loading tap shows the wait snackbar and starting a page resets state`() = runTest {
        config.candidates = listOf(candidate("a"), candidate("b"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadPage()
        vm.onIntent(BrowserIntent.Scan)
        advanceUntilIdle()
        assertThat(vm.state.value.candidates).hasSize(2)

        vm.onIntent(BrowserIntent.PageStarted("https://other.example/"))
        assertThat(vm.state.value.candidates).isEmpty()
        assertThat(vm.state.value.sheet).isEqualTo(BrowserSheet.None)
        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.PageLoading)
        assertThat(session.current.value).isNull()

        vm.effects.test {
            vm.onIntent(BrowserIntent.Scan)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.ShowSnackbar(labels.waitForPage))
        }
    }

    @Test
    fun `submit url infers https, keeps schemes and falls back to search`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(BrowserIntent.UpdateDraftUrl("cooking.example/pasta"))
            vm.onIntent(BrowserIntent.SubmitUrl)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.LoadUrl("https://cooking.example/pasta"))

            vm.onIntent(BrowserIntent.UpdateDraftUrl("http://plain.example"))
            vm.onIntent(BrowserIntent.SubmitUrl)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.LoadUrl("http://plain.example"))

            vm.onIntent(BrowserIntent.UpdateDraftUrl("best pasta recipe"))
            vm.onIntent(BrowserIntent.SubmitUrl)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.LoadUrl("https://search.example/?q=best pasta recipe"))
        }
        assertThat(vm.state.value.urlFocused).isFalse()
        assertThat(vm.state.value.isLoading).isTrue()
        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.PageLoading)
    }

    @Test
    fun `initial url is loaded on creation`() = runTest {
        val vm = viewModel(initialUrl = "https://start.example/")
        vm.effects.test {
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.LoadUrl("https://start.example/"))
        }
        assertThat(vm.state.value.url).isEqualTo("https://start.example/")
    }

    @Test
    fun `scrolling collapses and expands the button`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.fabExpanded).isTrue()
        vm.onIntent(BrowserIntent.Scrolled(40))
        assertThat(vm.state.value.fabExpanded).isFalse()
        vm.onIntent(BrowserIntent.Scrolled(-3))
        assertThat(vm.state.value.fabExpanded).isFalse()
        vm.onIntent(BrowserIntent.Scrolled(-40))
        assertThat(vm.state.value.fabExpanded).isTrue()
    }

    @Test
    fun `downloading readiness carries progress and an active extraction wins over idle`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadPage()
        session.setActiveExtraction("x")
        advanceUntilIdle()
        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.Extracting)

        vm.effects.test {
            vm.onIntent(BrowserIntent.Scan)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.NavigateToExtraction("x"))
        }

        readiness.readiness.value = ModelReadiness.Initializing
        advanceUntilIdle()
        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.PageLoading)
    }

    @Test
    fun `reopen request from extraction shows the candidate sheet again`() = runTest {
        config.candidates = listOf(candidate("a"), candidate("b"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadPage()
        vm.onIntent(BrowserIntent.Scan)
        advanceUntilIdle()
        vm.onIntent(BrowserIntent.PickCandidate("a"))
        advanceUntilIdle()
        assertThat(vm.state.value.sheet).isEqualTo(BrowserSheet.None)

        session.requestReopenCandidates()
        advanceUntilIdle()
        assertThat(vm.state.value.sheet).isEqualTo(BrowserSheet.Candidates)
        assertThat(session.reopenCandidates.value).isFalse()
    }

    @Test
    fun `resolveInput rules`() {
        val search: (String) -> String = { "S:$it" }
        assertThat(BrowserViewModel.resolveInput("", search)).isNull()
        assertThat(BrowserViewModel.resolveInput("  ", search)).isNull()
        assertThat(BrowserViewModel.resolveInput("example.com", search)).isEqualTo("https://example.com")
        assertThat(BrowserViewModel.resolveInput("localhost:8080", search)).isEqualTo("https://localhost:8080")
        assertThat(BrowserViewModel.resolveInput("ftp://x.y", search)).isEqualTo("ftp://x.y")
        assertThat(BrowserViewModel.resolveInput("pasta", search)).isEqualTo("S:pasta")
        assertThat(BrowserViewModel.resolveInput("pasta example.com", search)).isEqualTo("S:pasta example.com")
    }
}
