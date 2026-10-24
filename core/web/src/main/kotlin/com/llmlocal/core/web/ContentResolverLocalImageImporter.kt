package com.llmlocal.core.web

import android.content.Context
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import java.io.File

/** [LocalImageImporter] over `ContentResolver`; the body arrives with stream I2. */
class ContentResolverLocalImageImporter(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : LocalImageImporter {

    override suspend fun import(uri: String, into: File, maxLongEdgePx: Int): Outcome<ImportedImage> =
        TODO("I2")

    override suspend fun tile(
        source: ImportedImage,
        intoDir: File,
        tallAspect: Double,
        overlap: Double,
    ): Outcome<List<ImportedImage>> = TODO("I2")
}
