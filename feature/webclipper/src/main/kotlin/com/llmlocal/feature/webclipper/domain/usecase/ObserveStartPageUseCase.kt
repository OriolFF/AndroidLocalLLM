package com.llmlocal.feature.webclipper.domain.usecase

import com.llmlocal.feature.webclipper.domain.StartPage
import com.llmlocal.feature.webclipper.domain.StartPageRepository
import kotlinx.coroutines.flow.Flow

/** The saved browser [StartPage], as a flow ([StartPage.DEFAULT] until the user picks one). */
class ObserveStartPageUseCase(private val repository: StartPageRepository) {
    operator fun invoke(): Flow<StartPage> = repository.startPage
}
