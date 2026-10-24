package com.llmlocal.feature.webclipper.domain.input

import com.llmlocal.core.web.PageSnapshot
import java.io.File

/**
 * What a clip run reads from. The pipeline (phases, streaming, pictures,
 * save, cancel) is source-independent; only the first phase and the model
 * request differ per kind.
 */
sealed interface ClipInput {

    /** A captured page. */
    data class WebPage(val snapshot: PageSnapshot) : ClipInput

    /** Prepared pictures, in reading order. */
    data class Images(val images: List<ClipImage>) : ClipInput

    /** Placeholder for the future voice source; unused. */
    data class Voice(val transcript: String, val language: String? = null) : ClipInput
}

/**
 * One prepared picture (EXIF applied, JPEG, long edge capped). [tileOf] is
 * the index of the source picture when this is a tile of a tall one.
 */
data class ClipImage(
    val file: File,
    val width: Int,
    val height: Int,
    val sha256: String,
    val tileOf: Int? = null,
)

/** The captured page, or `null` for images and voice. */
val ClipInput.snapshotOrNull: PageSnapshot? get() = (this as? ClipInput.WebPage)?.snapshot

/** Coarse kind of a [ClipInput], for screens that branch without touching the session. */
enum class ClipInputKind { WEB, IMAGES, VOICE }

val ClipInput.kind: ClipInputKind
    get() = when (this) {
        is ClipInput.WebPage -> ClipInputKind.WEB
        is ClipInput.Images -> ClipInputKind.IMAGES
        is ClipInput.Voice -> ClipInputKind.VOICE
    }
