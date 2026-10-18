package com.llmlocal.recipekeeper.data

import java.io.File

/**
 * Owns the on-disk picture folders, `<root>/<recipeId>/`. The clip sink
 * moves freshly downloaded temp files in; the repository deletes the folder
 * on hard delete. Pure `java.io`, so it is unit-testable with a temp dir.
 *
 * @param root normally `context.filesDir/recipes`
 */
class RecipePictureStore(private val root: File) {

    fun dirFor(recipeId: String): File = File(root, recipeId)

    /**
     * Moves [files] into the recipe's folder as `001.jpg`, `002.jpg`, …
     * (keeping each source's extension when it has one) and returns the
     * new absolute paths in the same order. Falls back to copy + delete
     * when a rename crosses file systems. Sources that do not exist are
     * skipped.
     */
    fun moveIn(recipeId: String, files: List<File>): List<String> {
        val dir = dirFor(recipeId).apply { mkdirs() }
        return files.filter { it.isFile }.mapIndexedNotNull { index, source ->
            val ext = source.extension.takeIf { it.isNotBlank() } ?: "jpg"
            val target = File(dir, "%03d.%s".format(index + 1, ext))
            if (target.exists()) target.delete()
            val moved = source.renameTo(target) || runCatching {
                source.copyTo(target, overwrite = true)
                source.delete()
            }.isSuccess
            if (moved) target.absolutePath else null
        }
    }

    /** Deletes the recipe's folder and everything in it. Safe when absent. */
    fun deleteAll(recipeId: String) {
        dirFor(recipeId).deleteRecursively()
    }
}
