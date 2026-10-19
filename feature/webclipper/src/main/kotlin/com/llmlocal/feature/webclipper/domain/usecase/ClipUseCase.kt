package com.llmlocal.feature.webclipper.domain.usecase

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.domain.model.LlmTaskEvent
import com.llmlocal.core.domain.repository.LlmTaskParseException
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.domain.CandidateSource
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.feature.webclipper.domain.ClipProgress
import com.llmlocal.feature.webclipper.domain.ClipRequest
import com.llmlocal.feature.webclipper.domain.ClipSaveOptions
import com.llmlocal.feature.webclipper.domain.ClipSession
import com.llmlocal.feature.webclipper.domain.ClipSink
import com.llmlocal.feature.webclipper.domain.ExtractionError
import com.llmlocal.feature.webclipper.domain.LlmGenerationSource
import com.llmlocal.feature.webclipper.domain.Phase
import com.llmlocal.feature.webclipper.domain.PhaseStatus
import com.llmlocal.feature.webclipper.domain.PictureDownloader
import com.llmlocal.feature.webclipper.domain.PictureStatus
import com.llmlocal.feature.webclipper.domain.ProposedPicture
import com.llmlocal.feature.webclipper.domain.TempPictureStore
import com.llmlocal.feature.webclipper.domain.TruncationHeuristic
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/**
 * The whole extraction pipeline for one [ClipRequest] as a cold
 * [Flow] of [ClipProgress]: Reading → Extracting → Pictures → Saving.
 *
 *  0. **Direct path** — when `config.skipModelWhenStructuredComplete` is
 *     on and the candidate is STRUCTURED, `config.fallback` is computed
 *     first; if `config.isComplete` accepts it, Reading and Extracting are
 *     marked done at once ([ClipProgress.ExtractedWithoutModel]) and the
 *     flow continues at step 3 without touching the model.
 *  1. **Reading** — `config.renderLlmInput` on the captured snapshot.
 *  2. **Extracting** — [LlmGenerationSource.generate] with
 *     `maxOutputTokens = config.maxOutputTokens`; raw deltas are emitted as
 *     [ClipProgress.Streamed]. A parser `null` — or a result `config.accept`
 *     rejects — on a STRUCTURED candidate falls back to `config.fallback`.
 *  3. **Pictures** — `config.rankPictures` downloaded in parallel with the
 *     model (never sent to it); only the first [DEFAULT_KEEP] *preferred*
 *     picks start checked; failures only turn the phase to `Warning`.
 *  4. **Saving** — `sink.save` with the kept picture files, then the
 *     terminal [ClipProgress.Saved] and `session.clear()`.
 *
 * Failures are emitted as [ClipProgress.Failed] and complete the flow;
 * cancelling the collector stops the pictures, deletes the temp files and
 * releases `session.activeExtraction`. Stopping the engine itself is
 * [CancelClipUseCase]'s job.
 */
class ClipUseCase<T : Any>(
    private val llm: LlmGenerationSource,
    private val pictureDownloader: PictureDownloader,
    private val tempPictures: TempPictureStore,
    private val session: ClipSession,
    private val dispatchers: DispatcherProvider,
) {

    operator fun invoke(config: WebClipConfig<T>, sink: ClipSink<T>, request: ClipRequest): Flow<ClipProgress> =
        channelFlow {
            val run = Run(this, config, sink, request)
            session.setActiveExtraction(request.candidate.id)
            var saved = false
            try {
                saved = run.execute()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                send(ClipProgress.Failed(classify(e)))
            } finally {
                run.deleteTempFiles()
                if (!saved) session.setActiveExtraction(null)
            }
        }

    /** One execution of the pipeline; emits into [out]. */
    private inner class Run(
        private val out: ProducerScope<ClipProgress>,
        private val config: WebClipConfig<T>,
        private val sink: ClipSink<T>,
        private val request: ClipRequest,
    ) : CoroutineScope by out {
        private val candidate: ClipCandidate = request.candidate
        private val snapshot: PageSnapshot = request.snapshot

        private val tempFiles = mutableListOf<File>()
        private var keptFiles = emptyList<File>()

        /** Local file of every picture that downloaded, by URL. */
        private val downloaded = mutableMapOf<String, File>()
        private var proposed = emptyList<String>()

        /** Returns `true` once the item is saved, `false` after an emitted failure. */
        suspend fun execute(): Boolean {
            val direct = directResult()
            if (direct != null) {
                phase(Phase.Reading, PhaseStatus.Done)
                emit(ClipProgress.ExtractedWithoutModel)
                phase(Phase.Extracting, PhaseStatus.Done)
                return finish(direct, startPictures(), truncated = false)
            }

            phase(Phase.Reading, PhaseStatus.Active)
            val input = withContext(dispatchers.default) {
                config.renderLlmInput(candidate, snapshot, config.budgetChars)
            }
            phase(Phase.Reading, PhaseStatus.Done)
            phase(Phase.Extracting, PhaseStatus.Active)

            val pictures = startPictures()

            when (val init = withContext(dispatchers.io) { llm.initialize() }) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> return fail(pictures, classify(init.error))
            }

            val buffer = StringBuilder()
            var result: T? = null
            var failure: Throwable? = null
            llm.generate(config.llmConfig, input, config.maxOutputTokens).collect { event ->
                when (event) {
                    is LlmTaskEvent.Token -> {
                        buffer.append(event.delta)
                        emit(ClipProgress.Streamed(event.delta))
                    }
                    is LlmTaskEvent.Complete<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        result = event.result as T
                    }
                    is LlmTaskEvent.Failed -> failure = event.error
                }
            }
            val rawText = buffer.toString()

            val accepted = result?.let { config.accept(candidate, it) } ?: false
            if (!accepted) {
                val error = failure
                val parseFailed = result != null || error == null || error is LlmTaskParseException
                if (!parseFailed) return fail(pictures, classify(error))
                val fallback = if (candidate.source == CandidateSource.STRUCTURED) {
                    withContext(dispatchers.default) { config.fallback(candidate, snapshot) }
                } else {
                    null
                }
                if (fallback == null) return fail(pictures, ExtractionError.ParseFailed(rawText))
                result = fallback
            }

            val truncated = TruncationHeuristic.looksTruncated(rawText, config.maxOutputTokens)
            emit(ClipProgress.Truncated(truncated))
            phase(Phase.Extracting, if (truncated) PhaseStatus.Warning else PhaseStatus.Done)

            return finish(requireNotNull(result), pictures, truncated)
        }

        fun deleteTempFiles() {
            val doomed = synchronized(tempFiles) { tempFiles.filterNot { it in keptFiles } }
            doomed.forEach(tempPictures::delete)
        }

        /**
         * The deterministic result that replaces the model run, or `null` when
         * the switch is off, the candidate is not STRUCTURED, the mapping is
         * missing or [WebClipConfig.isComplete] rejects it.
         */
        private suspend fun directResult(): T? {
            if (!config.skipModelWhenStructuredComplete || candidate.source != CandidateSource.STRUCTURED) return null
            return withContext(dispatchers.default) {
                config.fallback(candidate, snapshot)?.takeIf { config.isComplete(it) }
            }
        }

        /** Pictures → Saving → [ClipProgress.Saved]; shared by the model and the direct path. */
        private suspend fun finish(result: T, pictures: Deferred<Boolean>, truncated: Boolean): Boolean {
            // The phase itself was settled by startPictures() the moment the
            // downloads finished (WS10 bug 9); only the result is read here.
            val anyPictureFailed = pictures.await()

            phase(Phase.Saving, PhaseStatus.Active)
            val kept = proposed
                .filter { request.isPictureKept(it) }
                .mapNotNull { downloaded[it] }
            // "Unavailable" = something was proposed and nothing came through
            // (offline, blocked CDN). A user who unticked every picture is not
            // told their pictures failed.
            val picturesFailed = anyPictureFailed && downloaded.isEmpty()
            val savedId = withContext(dispatchers.io) {
                sink.save(
                    result, snapshot, candidate, kept, truncated,
                    ClipSaveOptions(
                        replaceId = request.replaceId,
                        pictureCandidates = proposed,
                        picturesFailed = picturesFailed,
                    ),
                )
            }
            keptFiles = kept
            phase(Phase.Saving, PhaseStatus.Done)
            session.clear()
            emit(ClipProgress.Saved(savedId))
            return true
        }

        /**
         * Downloads the config's picture picks concurrently, each into its own
         * temp file, and marks [Phase.Pictures] Done / Warning as soon as they
         * have all settled — usually long before the model finishes, so the
         * ring does not spin for the whole run. Returns `true` when at least one
         * download failed. Never throws for a single failure; cancellation
         * propagates.
         */
        private fun startPictures(): Deferred<Boolean> = async {
            val picks = withContext(dispatchers.default) {
                config.rankPictures(candidate, snapshot, config.maxPictures)
            }.distinctBy { it.url }
            if (picks.isEmpty()) {
                phase(Phase.Pictures, PhaseStatus.Done) // nothing to download
                return@async false
            }
            proposed = picks.map { it.url }
            var checked = 0
            emit(
                ClipProgress.PicturesProposed(
                    picks.map { pick ->
                        val keep = pick.preferred && checked < DEFAULT_KEEP
                        if (keep) checked++
                        ProposedPicture(pick.url, keep)
                    },
                ),
            )
            phase(Phase.Pictures, PhaseStatus.Active)
            val runId = UUID.randomUUID().toString().take(8)
            val results = proposed.mapIndexed { index, url ->
                async(dispatchers.io) { downloadOne(url, tempPictures.newFile(runId, index)) }
            }.awaitAll()
            val anyFailed = results.any { !it }
            phase(Phase.Pictures, if (anyFailed) PhaseStatus.Warning else PhaseStatus.Done)
            anyFailed
        }

        /** `true` when [url] downloaded. */
        private suspend fun downloadOne(url: String, into: File): Boolean {
            synchronized(tempFiles) { tempFiles += into }
            emit(ClipProgress.PictureChanged(url, PictureStatus.Downloading))
            val outcome = try {
                pictureDownloader.download(url, snapshot.url, into, MAX_LONG_EDGE_PX)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.Failure(e)
            }
            return when (outcome) {
                is Outcome.Success -> {
                    synchronized(downloaded) { downloaded[url] = outcome.value }
                    emit(ClipProgress.PictureChanged(url, PictureStatus.Done, outcome.value))
                    true
                }
                is Outcome.Failure -> {
                    emit(ClipProgress.PictureChanged(url, PictureStatus.Failed))
                    false
                }
            }
        }

        private suspend fun fail(pictures: Deferred<Boolean>, error: ExtractionError): Boolean {
            pictures.cancel()
            emit(ClipProgress.Failed(error))
            return false
        }

        private suspend fun phase(phase: Phase, status: PhaseStatus) =
            emit(ClipProgress.PhaseChanged(phase, status))

        private suspend fun emit(event: ClipProgress) = out.send(event)
    }

    private fun classify(error: Throwable?): ExtractionError {
        val message = error?.message.orEmpty()
        val missing = error is FileNotFoundException ||
            message.contains("does not exist", ignoreCase = true) ||
            message.contains("no such file", ignoreCase = true)
        return if (missing) {
            ExtractionError.ModelMissing
        } else {
            ExtractionError.ModelFailed(message.ifBlank { error?.let { it::class.java.simpleName }.orEmpty() })
        }
    }

    companion object {
        /** How many *preferred* pictures start checked. */
        const val DEFAULT_KEEP = 3

        /** Long-edge resize bound handed to the downloader. */
        const val MAX_LONG_EDGE_PX = 1_280
    }
}
