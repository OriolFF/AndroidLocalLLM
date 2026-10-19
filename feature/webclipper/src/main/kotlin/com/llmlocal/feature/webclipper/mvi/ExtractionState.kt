package com.llmlocal.feature.webclipper.mvi

import androidx.compose.runtime.Immutable
import com.llmlocal.feature.webclipper.ClipCandidate
import java.io.File

/**
 * Single source of truth for the extraction screen.
 *
 * @property candidate the candidate being extracted
 * @property otherCandidates how many other candidates the page had (drives "Not this one?")
 * @property phases status of each [Phase], in [Phase] order
 * @property streamedText cumulative raw model output (the app's `renderDraft`
 *   slot runs its own incremental parse over it)
 * @property pictures picture downloads running in parallel with the model
 * @property truncated whether the output looked cut off at the token cap
 * @property extractedWithoutModel the item came from the config's deterministic
 *   mapping and the model was never run (`WebClipConfig.skipModelWhenStructuredComplete`)
 * @property showRaw raw-output toggle
 * @property savedId set once the sink has saved the item
 * @property sourceHost host of the captured page, for the source strip
 * @property pageTitle title of the captured page, for the source strip
 */
@Immutable
data class ExtractionState(
    val candidate: ClipCandidate,
    val otherCandidates: Int,
    val phases: Map<Phase, PhaseStatus>,
    val streamedText: String = "",
    val pictures: List<PictureDraft> = emptyList(),
    val isRunning: Boolean,
    val error: ExtractionError? = null,
    val truncated: Boolean = false,
    val extractedWithoutModel: Boolean = false,
    val showRaw: Boolean = false,
    val savedId: String? = null,
    val sourceHost: String? = null,
    val pageTitle: String? = null,
)

/** The four extraction phases, in display order. */
enum class Phase { Reading, Extracting, Pictures, Saving }

/** Per-phase status rendered by `PhaseProgress`. */
enum class PhaseStatus { Pending, Active, Done, Failed, Warning }

/**
 * One picture being downloaded for the item.
 *
 * @property keep user checkbox; the first three *preferred* picks
 *   (`PicturePick.preferred`) default to checked, every other pick starts
 *   unchecked
 */
data class PictureDraft(
    val url: String,
    val localFile: File? = null,
    val keep: Boolean = true,
    val status: PictureStatus = PictureStatus.Pending,
)

enum class PictureStatus { Pending, Downloading, Done, Failed }

/** Terminal failures shown in place of the draft card (UX spec §4 table). */
sealed interface ExtractionError {
    /** Engine init or generation failed. */
    data class ModelFailed(val reason: String) : ExtractionError

    /** Parser returned `null` at `Done` and no fallback applied. */
    data class ParseFailed(val rawOutput: String) : ExtractionError

    /** Model file disappeared mid-flow. */
    data object ModelMissing : ExtractionError
}
