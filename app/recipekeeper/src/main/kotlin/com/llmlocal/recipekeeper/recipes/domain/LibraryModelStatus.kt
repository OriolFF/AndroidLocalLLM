package com.llmlocal.recipekeeper.recipes.domain

import com.llmlocal.core.llm.download.PerModelProgress
import kotlinx.coroutines.flow.StateFlow

/**
 * The only readiness signal the library screen needs.
 *
 *  - [NoModel]     — the selected model is not on disk: show the hero that
 *                    sends the user to model management.
 *  - [Downloading] — a download for the selected model is RUNNING: show the
 *                    compact progress card instead of the hero.
 *  - [Ready]       — the file is installed: no banner; carries whether the
 *                    model can read images (for the source chooser).
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

    /** @property supportsImages the selected model can read pictures (`ModelCapability.VISION`) */
    data class Ready(val supportsImages: Boolean = false) : LibraryModelStatus
}

/** Hot source of [LibraryModelStatus]; implemented in `data/` over the shared model-readiness observer. */
interface LibraryModelStatusSource {
    val status: StateFlow<LibraryModelStatus>
}
