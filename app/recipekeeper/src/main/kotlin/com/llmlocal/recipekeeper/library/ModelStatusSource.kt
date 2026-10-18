package com.llmlocal.recipekeeper.library

import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.llm.readiness.ModelReadiness
import com.llmlocal.core.llm.readiness.ModelReadinessObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn


/**
 * The only readiness signal the library screen needs.
 *
 *  - [NoModel]     — the selected model is not on disk: show the hero that
 *                    sends the user to model management.
 *  - [Downloading] — a download for the selected model is RUNNING: show the
 *                    compact progress card instead of the hero.
 *  - [Ready]       — the file is installed: no banner.
 */
sealed interface LibraryModelStatus {
    data object NoModel : LibraryModelStatus

    data class Downloading(val progress: PerModelProgress) : LibraryModelStatus {
        /** 0–100, or 0 while the total size is still unknown. */
        val percent: Int
            get() = progress.totalBytes?.takeIf { it > 0 }
                ?.let { ((progress.bytesRead * 100) / it).toInt().coerceIn(0, 100) }
                ?: 0
    }

    data object Ready : LibraryModelStatus
}

/** Hot source of [LibraryModelStatus]; bound as a Koin `single` in `recipeKeeperUiModule`. */
interface ModelStatusSource {
    val status: StateFlow<LibraryModelStatus>
}

/** In-memory source for previews and ViewModel tests. */
class FakeModelStatusSource(
    initial: LibraryModelStatus = LibraryModelStatus.Ready,
) : ModelStatusSource {
    override val status: MutableStateFlow<LibraryModelStatus> = MutableStateFlow(initial)
}

/**
 * Bridges `:core:llm`'s selection + download-progress stores and the model
 * manager into a [LibraryModelStatus] flow.
 *
 * Re-evaluates whenever the selected model changes **or** the download
 * state for that model changes, so a fresh install whose default model is
 * already "selected" still flips to [LibraryModelStatus.Ready] when the
 * download finishes (the same trap `LlmTaskViewModel.observeSelection`
 * guards against).
 */
/**
 * Adapts the shared [ModelReadinessObserver] (`:core:llm`) to the library's
 * coarser [LibraryModelStatus]: anything that is not downloading or usable is
 * "no model" from the library's point of view, because the only action it
 * offers is "open model management".
 */
class ReadinessModelStatusSource(
    observer: ModelReadinessObserver,
    scope: CoroutineScope,
) : ModelStatusSource {
    override val status: StateFlow<LibraryModelStatus> = observer.observe(scope)
        .map { readiness ->
            when (readiness) {
                is ModelReadiness.Downloading -> LibraryModelStatus.Downloading(readiness.progress)
                is ModelReadiness.Ready, ModelReadiness.Initializing -> LibraryModelStatus.Ready
                ModelReadiness.NoModel, ModelReadiness.Checking, is ModelReadiness.Failed -> LibraryModelStatus.NoModel
            }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, LibraryModelStatus.NoModel)
}
