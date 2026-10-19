package com.llmlocal.feature.webclipper.data

import com.llmlocal.feature.webclipper.domain.TempPictureStore
import java.io.File

/** [TempPictureStore] rooted at [dir] (the app's cache sub-directory). */
class CacheDirTempPictureStore(private val dir: File) : TempPictureStore {

    override fun newFile(runId: String, index: Int): File =
        File(dir, "$runId-$index.jpg").also { it.parentFile?.mkdirs() }

    override fun delete(file: File) {
        runCatching { file.delete() }
    }
}
