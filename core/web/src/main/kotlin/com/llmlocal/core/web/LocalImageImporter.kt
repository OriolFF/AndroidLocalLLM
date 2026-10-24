package com.llmlocal.core.web

import com.llmlocal.core.common.result.Outcome
import java.io.File

/**
 * Makes a picked picture model- and storage-ready: JPEG q85, EXIF rotation
 * applied, long edge at most `maxLongEdgePx`. Brand-free; the feature layer
 * owns where the files live (`TempPictureStore`).
 */
interface LocalImageImporter {

    /** Decodes the content/file [uri] and writes the result to [into]. */
    suspend fun import(uri: String, into: File, maxLongEdgePx: Int): Outcome<ImportedImage>

    /**
     * Splits a tall [source] into overlapping tiles of aspect ≈ 1:1.6 written
     * under [intoDir]; `listOf(source)` when the picture is not taller than
     * [tallAspect] × its width.
     */
    suspend fun tile(
        source: ImportedImage,
        intoDir: File,
        tallAspect: Double,
        overlap: Double = 0.10,
    ): Outcome<List<ImportedImage>>
}

/** A picture written by [LocalImageImporter]. */
data class ImportedImage(val file: File, val width: Int, val height: Int, val sha256: String)
