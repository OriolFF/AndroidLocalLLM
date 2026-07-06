package com.llmlocal.feature.llmtask.mvi

/**
 * One-shot side effects emitted by the LLM-task ViewModel.
 *
 * Use [kotlinx.coroutines.channels.Channel] / `receiveAsFlow()` to consume
 * these in Compose without replays on configuration change.
 */
sealed interface LlmTaskEffect {
    data class ShowSnackbar(val message: String) : LlmTaskEffect
    data object ScrollToOutput : LlmTaskEffect

    /**
     * Tells the Route to navigate to the model management destination.
     * Consumed in [com.llmlocal.feature.llmtask.LlmTaskRoute] and
     * forwarded to the host's `NavController`.
     */
    data object NavigateToModelManagement : LlmTaskEffect
}