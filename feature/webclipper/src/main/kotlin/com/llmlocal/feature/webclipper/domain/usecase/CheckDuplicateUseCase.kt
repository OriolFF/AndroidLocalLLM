package com.llmlocal.feature.webclipper.domain.usecase

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.domain.ClipSink
import com.llmlocal.feature.webclipper.domain.input.ClipInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Asks the sink whether [PageSnapshot]'s page is already saved. A sink that
 * throws counts as "no duplicate" — the warning is a courtesy, not a gate.
 */
class CheckDuplicateUseCase(private val dispatchers: DispatcherProvider) {

    /** Id of the item already saved from [snapshot]'s page, or `null`. */
    suspend operator fun invoke(sink: ClipSink<*>, snapshot: PageSnapshot): String? =
        withContext(dispatchers.io) {
            try {
                sink.existingId(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
        }

    /** Input-aware overload; sinks answer `null` for inputs without a page. */
    suspend operator fun invoke(sink: ClipSink<*>, input: ClipInput): String? =
        withContext(dispatchers.io) {
            try {
                sink.existingId(input)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
        }
}
