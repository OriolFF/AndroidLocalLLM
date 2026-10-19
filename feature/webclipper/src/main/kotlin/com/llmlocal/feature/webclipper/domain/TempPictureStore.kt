package com.llmlocal.feature.webclipper.domain

import java.io.File

/** Scratch space for pictures downloaded before the sink moves them to their final home. */
interface TempPictureStore {

    /** A fresh file for picture [index] of the run [runId]; its parent directory exists. */
    fun newFile(runId: String, index: Int): File

    fun delete(file: File)
}
