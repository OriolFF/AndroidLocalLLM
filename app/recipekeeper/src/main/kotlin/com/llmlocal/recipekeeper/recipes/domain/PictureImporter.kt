package com.llmlocal.recipekeeper.recipes.domain

import com.llmlocal.core.common.result.Outcome
import java.io.File

/** Copies pictures picked by the user into app storage, downscaled. */
interface PictureImporter {

    /**
     * Copies the picture at [uri] (`content://`, `file://`) into [into] as a
     * JPEG whose long edge is at most [maxLongEdgePx].
     */
    suspend fun import(uri: String, into: File, maxLongEdgePx: Int = 1_280): Outcome<File>

    /** Deletes temp files under the editor cache older than [olderThanMillis] (epoch). */
    fun sweep(olderThanMillis: Long)
}
