package com.llmlocal.recipekeeper.testing

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.recipekeeper.recipes.domain.PictureImporter
import java.io.File

/** Writes a tiny file at the requested target for every uri not in [failing]. */
class FakePictureImporter(private val failing: Set<String> = emptySet()) : PictureImporter {
    val imported = mutableListOf<String>()
    var sweptBefore: Long? = null

    override suspend fun import(uri: String, into: File, maxLongEdgePx: Int): Outcome<File> {
        imported += uri
        if (uri in failing) return Outcome.Failure(IllegalStateException("cannot import $uri"))
        into.parentFile?.mkdirs()
        into.writeBytes(byteArrayOf(1, 2, 3))
        return Outcome.Success(into)
    }

    override fun sweep(olderThanMillis: Long) {
        sweptBefore = olderThanMillis
    }
}
