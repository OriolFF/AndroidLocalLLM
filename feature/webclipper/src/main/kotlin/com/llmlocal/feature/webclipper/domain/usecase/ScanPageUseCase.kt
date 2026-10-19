package com.llmlocal.feature.webclipper.domain.usecase

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.feature.webclipper.domain.ClipSession
import com.llmlocal.feature.webclipper.domain.PageScanner
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Looks at the page currently shown: a cheap structured-data [quick] count
 * for the proactive clip-button badge, and the full [invoke] capture +
 * `config.detect` that fills the [ClipSession] with the page and its candidates.
 */
class ScanPageUseCase(
    private val scanner: PageScanner,
    private val session: ClipSession,
    private val dispatchers: DispatcherProvider,
) {

    /** Number of structured candidates, or `0` when the cheap scan fails. */
    suspend fun quick(): Int = try {
        scanner.quickScanStructured()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        0
    }

    /**
     * Captures the page, detects at most [MAX_CANDIDATES] candidates with
     * [config] and stores both in the session.
     */
    suspend operator fun invoke(config: WebClipConfig<*>): Outcome<List<ClipCandidate>> =
        when (val outcome = scanner.capture()) {
            is Outcome.Success -> {
                val snapshot = outcome.value
                val candidates = withContext(dispatchers.default) {
                    config.detect(snapshot).take(MAX_CANDIDATES)
                }
                session.set(snapshot, candidates)
                Outcome.Success(candidates)
            }
            is Outcome.Failure -> Outcome.Failure(outcome.error)
        }

    companion object {
        const val MAX_CANDIDATES = 10
    }
}
