package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.EditSection

/** Splits pasted multi-line text into editor rows, stripping bullets and numbering. */
class SplitPastedLinesUseCase {

    operator fun invoke(text: String, section: EditSection): List<String> {
        TODO("F3")
    }
}
