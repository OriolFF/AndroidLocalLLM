package com.llmlocal.feature.webclipper.presentation.mvi

import androidx.compose.runtime.Immutable
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.feature.webclipper.domain.ExtractionError
import com.llmlocal.feature.webclipper.domain.Phase
import com.llmlocal.feature.webclipper.domain.PhaseStatus
import com.llmlocal.feature.webclipper.domain.PictureStatus
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
 * @property duplicateOf id of an item already saved from this page; while
 *   non-null the duplicate warning dialog is up and nothing runs
 * @property replaceId the user chose "Replace": the save reuses this id
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
    val duplicateOf: String? = null,
    val replaceId: String? = null,
)

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
