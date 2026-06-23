package com.llmlocal.feature.modelmanagement.mvi

/**
 * User-originated actions for the model management screen.
 *
 * Add a new sealed case whenever a new user gesture needs to be handled.
 */
sealed interface ModelManagementIntent {
    /** Begin downloading the model identified by [modelId]. */
    data class StartDownload(val modelId: String) : ModelManagementIntent

    /** Cancel an in-flight download. */
    data class CancelDownload(val modelId: String) : ModelManagementIntent

    /** Delete the model file from disk. */
    data class Remove(val modelId: String) : ModelManagementIntent

    /**
     * Mark [modelId] as the active model. The recipe engine's path
     * provider will resolve to this model on its next `initialize()`.
     */
    data class Select(val modelId: String) : ModelManagementIntent

    /** Open the details bottom sheet for [modelId]. */
    data class ShowDetails(val modelId: String) : ModelManagementIntent

    /** Close the bottom sheet. */
    data object DismissDetails : ModelManagementIntent

    /** Clears the [ModelManagementState.lastError] field. */
    data object DismissError : ModelManagementIntent

    /** Re-scans disk for installed models (e.g. after a manual refresh). */
    data object Refresh : ModelManagementIntent

    /**
     * Replace the active filter set. The view model recomputes
     * [ModelManagementState.filteredCatalog] from `catalog` +
     * `installedIds` + `downloading` + the new filters atomically.
     */
    data class SetFilters(val filters: ModelFilters) : ModelManagementIntent

    /** Reset every filter back to its default. */
    data object ClearFilters : ModelManagementIntent
}
