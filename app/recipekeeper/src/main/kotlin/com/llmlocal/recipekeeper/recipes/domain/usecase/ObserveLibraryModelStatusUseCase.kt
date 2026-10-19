package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatus
import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatusSource
import kotlinx.coroutines.flow.StateFlow

/** Hot [LibraryModelStatus] for the library's no-model hero / download card. */
class ObserveLibraryModelStatusUseCase(private val source: LibraryModelStatusSource) {
    operator fun invoke(): StateFlow<LibraryModelStatus> = source.status
}
