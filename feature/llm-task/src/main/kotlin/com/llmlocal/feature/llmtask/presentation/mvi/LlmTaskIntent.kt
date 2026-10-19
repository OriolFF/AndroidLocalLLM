package com.llmlocal.feature.llmtask.presentation.mvi

/**
 * All user-originated actions the LLM-task feature supports.
 *
 * Inputs are typed `String` (rather than a domain type like `Ingredient`)
 * so the scaffold works for any "list of inputs → structured output" app —
 * recipe ingredients, medical symptoms, etc. Validation/normalization is the
 * consuming app's responsibility.
 *
 * Add a new sealed case whenever a new user gesture needs to be handled.
 */
sealed interface LlmTaskIntent {
    /** Text in the input field changed (the user typed). */
    data class UpdateInput(val text: String) : LlmTaskIntent

    /** Add the current input value to the list of inputs. */
    data object AddInput : LlmTaskIntent

    /** Remove an input by its current text value. */
    data class RemoveInput(val text: String) : LlmTaskIntent

    /** Clear the entire input list. */
    data object ClearInputs : LlmTaskIntent

    /** Kick off a new LLM generation pass with the current inputs. */
    data object Generate : LlmTaskIntent

    /** Cancel any in-flight generation. */
    data object CancelGeneration : LlmTaskIntent

    /** Dismiss the error message. */
    data object DismissError : LlmTaskIntent

    /**
     * Open the model management screen (no-op if already there). Used by
     * [com.llmlocal.feature.llmtask.presentation.components.NoModelAvailableBanner] and
     * any "manage models" entry points.
     */
    data object OpenModelManagement : LlmTaskIntent

    /**
     * Re-check the disk for the currently selected model. Useful after
     * the user returns from the model-management screen and has finished
     * downloading or switching models.
     */
    data object CheckModel : LlmTaskIntent
}