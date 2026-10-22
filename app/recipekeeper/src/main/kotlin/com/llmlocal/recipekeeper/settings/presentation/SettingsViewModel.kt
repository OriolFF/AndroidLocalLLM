package com.llmlocal.recipekeeper.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.feature.webclipper.domain.StartPage
import com.llmlocal.feature.webclipper.domain.usecase.ObserveStartPageUseCase
import com.llmlocal.feature.webclipper.domain.usecase.SetStartPageUseCase
import com.llmlocal.recipekeeper.settings.domain.normalizeStartPageUrl
import com.llmlocal.recipekeeper.settings.presentation.mvi.SettingsEffect
import com.llmlocal.recipekeeper.settings.presentation.mvi.SettingsIntent
import com.llmlocal.recipekeeper.settings.presentation.mvi.SettingsState
import com.llmlocal.recipekeeper.settings.presentation.mvi.StartPageOption
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MVI ViewModel for the settings screen: the browser start page (Google /
 * blank / custom address) and the entry to model management.
 *
 * Google and Blank are saved the moment they are picked. Custom is saved
 * when the field commits a valid address (or when the radio is picked while
 * the field already holds one); an invalid address flags the field and
 * leaves the previous choice in place.
 */
class SettingsViewModel(
    private val observeStartPage: ObserveStartPageUseCase,
    private val setStartPage: SetStartPageUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val saved = withContext(dispatchers.io) { observeStartPage().first() }
            _state.update {
                it.copy(
                    isLoading = false,
                    startPage = saved.toOption(),
                    customUrl = (saved as? StartPage.Custom)?.url.orEmpty(),
                )
            }
        }
    }

    /** Single dispatch point for every user action. */
    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectStartPage -> select(intent.option)
            is SettingsIntent.UpdateCustomUrl ->
                _state.update { it.copy(customUrl = intent.value, customUrlError = false) }
            SettingsIntent.CommitCustomUrl -> commitCustomUrl()
            SettingsIntent.OpenModelManagement -> _effects.trySend(SettingsEffect.NavigateToModelManagement)
        }
    }

    private fun select(option: StartPageOption) {
        _state.update { it.copy(startPage = option, customUrlError = false) }
        when (option) {
            StartPageOption.GOOGLE -> persist(StartPage.Google)
            StartPageOption.BLANK -> persist(StartPage.Blank)
            StartPageOption.CUSTOM -> normalizeStartPageUrl(_state.value.customUrl)?.let { persist(StartPage.Custom(it)) }
        }
    }

    private fun commitCustomUrl() {
        val normalized = normalizeStartPageUrl(_state.value.customUrl)
        if (normalized == null) {
            _state.update { it.copy(customUrlError = true) }
            return
        }
        _state.update { it.copy(startPage = StartPageOption.CUSTOM, customUrl = normalized, customUrlError = false) }
        persist(StartPage.Custom(normalized))
    }

    private fun persist(page: StartPage) {
        viewModelScope.launch { withContext(dispatchers.io) { setStartPage(page) } }
    }

    private fun StartPage.toOption(): StartPageOption = when (this) {
        StartPage.Google -> StartPageOption.GOOGLE
        StartPage.Blank -> StartPageOption.BLANK
        is StartPage.Custom -> StartPageOption.CUSTOM
    }
}
