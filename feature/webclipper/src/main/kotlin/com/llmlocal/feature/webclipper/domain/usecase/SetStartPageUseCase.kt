package com.llmlocal.feature.webclipper.domain.usecase

import com.llmlocal.feature.webclipper.domain.StartPage
import com.llmlocal.feature.webclipper.domain.StartPageRepository

/** Saves the browser [StartPage] chosen on the settings screen. */
class SetStartPageUseCase(private val repository: StartPageRepository) {
    suspend operator fun invoke(page: StartPage) = repository.set(page)
}
