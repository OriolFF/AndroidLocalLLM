package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.recipes.domain.DetailBackdrop
import com.llmlocal.recipekeeper.recipes.domain.DetailBackdropRepository
import kotlinx.coroutines.flow.Flow

/** The saved [DetailBackdrop], live. */
class ObserveDetailBackdropUseCase(private val repository: DetailBackdropRepository) {
    operator fun invoke(): Flow<DetailBackdrop> = repository.backdrop
}
