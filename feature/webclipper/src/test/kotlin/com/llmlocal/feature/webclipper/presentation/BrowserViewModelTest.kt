package com.llmlocal.feature.webclipper.presentation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.core.model.ModelFamily
import com.llmlocal.feature.webclipper.domain.CandidatePayload
import com.llmlocal.feature.webclipper.domain.WHOLE_PAGE_CANDIDATE_ID
import com.llmlocal.feature.webclipper.data.InMemoryClipSession
import com.llmlocal.feature.webclipper.domain.ClipSession
import com.llmlocal.feature.webclipper.domain.StartPage
import com.llmlocal.feature.webclipper.domain.usecase.ObserveReadinessUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ObserveStartPageUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ScanPageUseCase
import com.llmlocal.feature.webclipper.fakes.FakePageScanner
import com.llmlocal.feature.webclipper.fakes.FakeReadinessSource
import com.llmlocal.feature.webclipper.fakes.FakeStartPageRepository
import com.llmlocal.feature.webclipper.fakes.FakeWebClipConfig
import com.llmlocal.feature.webclipper.fakes.MainDispatcherRule
import com.llmlocal.feature.webclipper.fakes.TestDispatchers
import com.llmlocal.feature.webclipper.fakes.candidate
import com.llmlocal.feature.webclipper.presentation.mvi.BrowserEffect
import com.llmlocal.feature.webclipper.presentation.mvi.BrowserIntent
import com.llmlocal.feature.webclipper.presentation.mvi.BrowserSheet
import com.llmlocal.feature.webclipper.presentation.mvi.ClipFabState
import com.llmlocal.core.llm.readiness.ModelReadiness
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
    private val labels = testWebClipperLabels()
    private val session: ClipSession = InMemoryClipSession()
    private val readiness = FakeReadinessSource(ModelReadiness.Ready(descriptor))
    private val scanner = FakePageScanner()
    private val config = FakeWebClipConfig()
    private val startPages = FakeStartPageRepository()

    private fun viewModel(initialUrl: String? = null) = BrowserViewModel(
        config = config,
        labels = labels,
        scanPage = ScanPageUseCase(scanner, session, TestDispatchers(mainDispatcherRule.dispatcher)),
        observeReadiness = ObserveReadinessUseCase(readiness),
        observeStartPage = ObserveStartPageUseCase(startPages),
        session = session,
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
    fun `a save clears the cached candidates and the next tap re-scans the page`() = runTest {
        config.candidates = listOf(candidate("only"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadPage()
        vm.effects.test {
            vm.onIntent(BrowserIntent.Scan)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.NavigateToExtraction("only"))
        }
        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.Found(1))
        assertThat(scanner.captures).isEqualTo(1)

        // The extraction screen saved the recipe and cleared the session.
        session.clear()
        advanceUntilIdle()
        assertThat(vm.state.value.candidates).isEmpty()
        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.Idle)

        vm.effects.test {
            vm.onIntent(BrowserIntent.Scan)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.NavigateToExtraction("only"))
        }
        assertThat(scanner.captures).isEqualTo(2)
        assertThat(session.current.value?.candidates?.map { it.id }).containsExactly("only")
    }

    @Test
    fun `a cleared session also drops the proactive quick-scan badge`() = runTest {
        scanner.quickCount = 1
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadPage()
        advanceTimeBy(BrowserViewModel.QUICK_SCAN_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.Found(1))

        // Capture (populates the session) …
        config.candidates = listOf(candidate("only"))
        vm.onIntent(BrowserIntent.Scan)
        advanceUntilIdle()
        // … then save (clears it): the badge must not fall back to the stale quick-scan count.
        session.clear()
        advanceUntilIdle()
        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.Idle)
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
    fun `downloading model tap navigates to model management without the snackbar`() = runTest {
        val progress = PerModelProgress(
            modelId = descriptor.id, state = DownloadState.RUNNING, bytesRead = 50, totalBytes = 100,
            speedBytesPerSec = 1, etaSeconds = 1, updatedAt = 0,
        )
        readiness.readiness.value = ModelReadiness.Downloading(progress)
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadPage()

        assertThat(vm.state.value.fab).isEqualTo(ClipFabState.ModelNotReady(progress))
        vm.effects.test {
            vm.onIntent(BrowserIntent.Scan)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.NavigateToModelManagement)
            expectNoEvents()
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
    fun `hide for page is remembered per host and restored by ShowFab`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadPage("https://www.cooking.example/recipes/pasta")
        assertThat(vm.state.value.fabHiddenForPage).isFalse()

        vm.onIntent(BrowserIntent.HideFabForPage)
        assertThat(vm.state.value.fabHiddenForPage).isTrue()
        assertThat(vm.state.value.hiddenFabHosts).containsExactly("cooking.example")

        // Another host: the button is back; the choice is not forgotten.
        vm.loadPage("https://other.example/soup")
        assertThat(vm.state.value.fabHiddenForPage).isFalse()
        assertThat(vm.state.value.hiddenFabHosts).containsExactly("cooking.example")

        // Back on the same host (www-less this time): still hidden.
        vm.loadPage("https://cooking.example/recipes/soup")
        assertThat(vm.state.value.fabHiddenForPage).isTrue()

        vm.onIntent(BrowserIntent.ShowFab)
        assertThat(vm.state.value.fabHiddenForPage).isFalse()
        assertThat(vm.state.value.hiddenFabHosts).isEmpty()
    }

    @Test
    fun `hide for page is a no-op without a page`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(BrowserIntent.HideFabForPage)
        assertThat(vm.state.value.hiddenFabHosts).isEmpty()
        assertThat(vm.state.value.fabHiddenForPage).isFalse()
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

    // -- start page ----------------------------------------------------------

    @Test
    fun `blank start loads the saved start page, Google by default`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(BrowserIntent.OpenStartPage)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.LoadUrl(StartPage.Google.url))
        }
        assertThat(vm.state.value.url).isEqualTo(StartPage.Google.url)
        assertThat(vm.state.value.urlFocused).isFalse()
    }

    @Test
    fun `blank start loads a custom start page`() = runTest {
        startPages.current.value = StartPage.Custom("https://www.cooking.example/")
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(BrowserIntent.OpenStartPage)
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(BrowserEffect.LoadUrl("https://www.cooking.example/"))
        }
    }

    @Test
    fun `blank start page focuses the address bar instead of loading`() = runTest {
        startPages.current.value = StartPage.Blank
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(BrowserIntent.OpenStartPage)
            advanceUntilIdle()
            expectNoEvents()
        }
        assertThat(vm.state.value.url).isEmpty()
        assertThat(vm.state.value.urlFocused).isTrue()
    }

    @Test
    fun `start page is ignored once a page is shown`() = runTest {
        val vm = viewModel(initialUrl = "https://www.cooking.example/recipes/pasta")
        advanceUntilIdle()

        vm.effects.test {
            // The constructor's own LoadUrl is still buffered.
            assertThat(awaitItem()).isEqualTo(BrowserEffect.LoadUrl("https://www.cooking.example/recipes/pasta"))
            vm.onIntent(BrowserIntent.OpenStartPage)
            advanceUntilIdle()
            expectNoEvents()
        }
        assertThat(vm.state.value.url).isEqualTo("https://www.cooking.example/recipes/pasta")
    }
}
