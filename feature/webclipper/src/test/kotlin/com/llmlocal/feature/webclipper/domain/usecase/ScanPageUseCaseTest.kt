package com.llmlocal.feature.webclipper.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.feature.webclipper.data.InMemoryClipSession
import com.llmlocal.feature.webclipper.fakes.FakePageScanner
import com.llmlocal.feature.webclipper.fakes.FakeWebClipConfig
import com.llmlocal.feature.webclipper.fakes.TestDispatchers
import com.llmlocal.feature.webclipper.fakes.candidate
import com.llmlocal.feature.webclipper.fakes.snapshot
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ScanPageUseCaseTest {

    private fun scanUseCase(scanner: FakePageScanner, testDispatcher: TestDispatcher) =
        ScanPageUseCase(scanner, InMemoryClipSession(), TestDispatchers(testDispatcher))

    @Test
    fun `tells the config which host loaded before detecting`() = runTest {
        val config = FakeWebClipConfig(candidates = listOf(candidate("c0")))
        val scanner = FakePageScanner(captureOutcome = Outcome.Success(snapshot(url = "https://www.Example.com/recipes/a")))

        scanUseCase(scanner, StandardTestDispatcher(testScheduler))(config)

        assertThat(config.pageLoadedHosts).containsExactly("example.com")
        assertThat(config.callOrder).containsExactly("loaded:example.com", "detect").inOrder()
    }

    @Test
    fun `a capture failure never reaches the config`() = runTest {
        val config = FakeWebClipConfig()
        val scanner = FakePageScanner(captureOutcome = Outcome.Failure(IllegalStateException("no page")))

        val outcome = scanUseCase(scanner, StandardTestDispatcher(testScheduler))(config)

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat(config.callOrder).isEmpty()
    }

    @Test
    fun `an unparsable url yields a null host`() = runTest {
        val config = FakeWebClipConfig()
        val scanner = FakePageScanner(captureOutcome = Outcome.Success(snapshot(url = "about:blank")))

        scanUseCase(scanner, StandardTestDispatcher(testScheduler))(config)

        assertThat(config.pageLoadedHosts).containsExactly(null)
    }

    @Test
    fun `candidates default to not hint-detected`() {
        assertThat(candidate("c0").detectedByHints).isFalse()
    }
}
