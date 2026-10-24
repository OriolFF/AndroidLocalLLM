package com.llmlocal.recipekeeper.recipes.data

import com.llmlocal.core.llm.readiness.ModelReadiness
import com.llmlocal.core.llm.readiness.ModelReadinessObserver
import com.llmlocal.core.model.ModelCapability
import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatus
import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatusSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Adapts the shared [ModelReadinessObserver] (`:core:llm`) to the library's
 * coarser [LibraryModelStatus]: anything that is not downloading or usable is
 * "no model" from the library's point of view, because the only action it
 * offers is "open model management".
 *
 * The observer re-evaluates when the selected model changes **or** its
 * download state changes, so a fresh install whose default model is already
 * "selected" still flips to [LibraryModelStatus.Ready] when the download
 * finishes.
 */
class ReadinessLibraryModelStatusSource(
    observer: ModelReadinessObserver,
    scope: CoroutineScope,
) : LibraryModelStatusSource {
    override val status: StateFlow<LibraryModelStatus> = observer.observe(scope)
        .map { readiness ->
            when (readiness) {
                is ModelReadiness.Downloading -> LibraryModelStatus.Downloading(readiness.progress)
                is ModelReadiness.Ready ->
                    LibraryModelStatus.Ready(
                        supportsImages = ModelCapability.VISION in readiness.descriptor.capabilities,
                        modelName = readiness.descriptor.displayName,
                    )
                ModelReadiness.Initializing -> LibraryModelStatus.Ready()
                ModelReadiness.NoModel, ModelReadiness.Checking, is ModelReadiness.Failed -> LibraryModelStatus.NoModel
            }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, LibraryModelStatus.NoModel)
}
