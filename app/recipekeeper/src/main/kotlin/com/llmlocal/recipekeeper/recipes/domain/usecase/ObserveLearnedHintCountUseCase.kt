package com.llmlocal.recipekeeper.recipes.domain.usecase

import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHintsStore
import kotlinx.coroutines.flow.Flow

/** How many detection hints Recipe Keeper has learned so far (shown next to "Reset learned detection"). */
class ObserveLearnedHintCountUseCase(private val store: DetectionHintsStore) {
    operator fun invoke(): Flow<Int> = store.count()
}
