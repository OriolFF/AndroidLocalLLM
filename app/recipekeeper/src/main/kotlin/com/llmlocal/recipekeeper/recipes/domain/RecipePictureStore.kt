package com.llmlocal.recipekeeper.recipes.domain

import java.io.File

/**
 * Owns the on-disk picture folders of saved recipes. The clip sink and
 * "Retry pictures" move freshly downloaded temp files in; the repository
 * deletes a recipe's folder on hard delete.
 */
interface RecipePictureStore {

    /** Folder that holds [recipeId]'s pictures (may not exist yet). */
    fun dirFor(recipeId: String): File

    /**
     * Moves [files] into the recipe's folder, numbered in order, and returns
     * the new absolute paths in the same order. Sources that do not exist are
     * skipped. Numbering continues after the highest existing `NNN.ext` in
     * the folder so nothing already stored is clobbered.
     */
    fun moveIn(recipeId: String, files: List<File>): List<String>

    /** Deletes exactly these stored files of [recipeId]; ignores paths outside its folder. */
    fun delete(recipeId: String, paths: List<String>)

    /** Deletes the recipe's folder and everything in it. Safe when absent. */
    fun deleteAll(recipeId: String)
}
