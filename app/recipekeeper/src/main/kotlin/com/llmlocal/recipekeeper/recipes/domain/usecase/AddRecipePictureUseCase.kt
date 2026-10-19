package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.recipekeeper.recipes.domain.EditorPicture
import com.llmlocal.recipekeeper.recipes.domain.PictureImporter
import java.io.File

/** Imports a picked picture into the editor's temp folder; it is committed on save. */
class AddRecipePictureUseCase(
    private val importer: PictureImporter,
    private val tempRoot: File,
    private val dispatchers: DispatcherProvider,
) {

    suspend operator fun invoke(sessionId: String, uri: String): Outcome<EditorPicture> {
        TODO("F3")
    }
}
