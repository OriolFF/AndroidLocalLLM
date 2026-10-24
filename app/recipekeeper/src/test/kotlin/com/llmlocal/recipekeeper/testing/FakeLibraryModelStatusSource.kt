package com.llmlocal.recipekeeper.testing

import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatus
import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatusSource
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [LibraryModelStatusSource] for ViewModel tests. */
class FakeLibraryModelStatusSource(
    initial: LibraryModelStatus = LibraryModelStatus.Ready(),
) : LibraryModelStatusSource {
    override val status: MutableStateFlow<LibraryModelStatus> = MutableStateFlow(initial)
}
