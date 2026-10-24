package com.llmlocal.feature.webclipper.domain

import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.domain.input.ClipInput
import com.llmlocal.feature.webclipper.domain.input.snapshotOrNull
import java.io.File

/** The four clip phases, in display order. */
enum class Phase { Reading, Extracting, Pictures, Saving }

/** Status of one [Phase]. */
enum class PhaseStatus { Pending, Active, Done, Failed, Warning }

/** Download state of one proposed picture. */
enum class PictureStatus { Pending, Downloading, Done, Failed }

/** Terminal failures of a clip run. */
sealed interface ExtractionError {
    /** Engine init or generation failed. */
    data class ModelFailed(val reason: String) : ExtractionError

    /** Parser returned `null` at `Done` and no fallback applied. */
    data class ParseFailed(val rawOutput: String) : ExtractionError

    /** Model file disappeared mid-flow. */
    data object ModelMissing : ExtractionError

    /** The selected model cannot read the pictures of an images clip. */
    data object ImagesUnsupported : ExtractionError
}

/**
 * What one [com.llmlocal.feature.webclipper.domain.usecase.ClipUseCase] run needs.
 *
 * @property replaceId id of the previously saved item the user chose to replace
 * @property isPictureKept the user's current checkbox for a picture URL; asked
 *   once, right before saving, so toggles made while the model runs count
 */
class ClipRequest(
    val candidate: ClipCandidate,
    val input: ClipInput,
    val replaceId: String? = null,
    val isPictureKept: (url: String) -> Boolean = { true },
) {
    constructor(
        candidate: ClipCandidate,
        snapshot: PageSnapshot,
        replaceId: String? = null,
        isPictureKept: (url: String) -> Boolean = { true },
    ) : this(candidate, ClipInput.WebPage(snapshot), replaceId, isPictureKept)

    /** The captured page, or `null` for images and voice. */
    val snapshot: PageSnapshot? get() = input.snapshotOrNull
}

/** A picture the config proposed; [keep] is its initial checkbox. */
data class ProposedPicture(val url: String, val keep: Boolean)

/** Events of a clip run, in the order the pipeline produces them. */
sealed interface ClipProgress {
    data class PhaseChanged(val phase: Phase, val status: PhaseStatus) : ClipProgress

    /** Raw model output delta. */
    data class Streamed(val delta: String) : ClipProgress

    /** The item came from the deterministic mapping; the model never ran. */
    data object ExtractedWithoutModel : ClipProgress

    data class PicturesProposed(val pictures: List<ProposedPicture>) : ClipProgress

    data class PictureChanged(
        val url: String,
        val status: PictureStatus,
        val localFile: File? = null,
    ) : ClipProgress

    /**
     * An images run got no recipe out of its pictures and starts again with
     * fewer of them: the streamed text so far is void and the phases restart
     * at Reading. Emitted at most once per run.
     */
    data class Retrying(val imageCount: Int) : ClipProgress

    /** Whether the model output looked cut off at the token cap. */
    data class Truncated(val truncated: Boolean) : ClipProgress

    data class Failed(val error: ExtractionError) : ClipProgress

    /** Terminal success: the sink saved the item under [id]. */
    data class Saved(val id: String) : ClipProgress
}
