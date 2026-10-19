package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository
import com.llmlocal.recipekeeper.recipes.domain.RecipeSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The library list: every recipe when the query is blank, else the search
 * result, minus recipes inside the delete-undo window (so they vanish on
 * every screen at once).
 */
class ObserveLibraryUseCase(
    private val repository: RecipeRepository,
    private val deleteWithUndo: DeleteRecipeWithUndoUseCase,
) {
    operator fun invoke(query: String): Flow<List<RecipeSummary>> {
        val source = if (query.isBlank()) repository.observeAll() else repository.search(query)
        return combine(source, deleteWithUndo.pending) { recipes, pending ->
            if (pending.isEmpty()) recipes else recipes.filterNot { it.id in pending }
        }
    }
}
