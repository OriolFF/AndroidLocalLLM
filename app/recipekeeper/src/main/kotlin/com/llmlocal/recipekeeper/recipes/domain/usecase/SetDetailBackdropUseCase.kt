package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.DetailBackdrop
import com.llmlocal.recipekeeper.recipes.domain.DetailBackdropRepository

/** Persists the user's [DetailBackdrop] choice. */
class SetDetailBackdropUseCase(private val repository: DetailBackdropRepository) {
    suspend operator fun invoke(backdrop: DetailBackdrop) = repository.set(backdrop)
}
