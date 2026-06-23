package com.llmlocal.feature.modelmanagement.mvi

import androidx.compose.runtime.Immutable
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.model.LlmModelDescriptor

/**
 * Aggregated UI state for the model management screen.
 *
 * @property catalog All models known to the app (source of truth =
 *   `LlmModelCatalog.ALL`).
 * @property installedIds Set of catalog ids whose files are on disk.
 * @property selectedId Currently-active model id, or `null` if nothing is
 *   selected.
 * @property downloading Per-model live progress. Models not currently
 *   downloading are absent from the map.
 * @property filters User-applied filter set; see [ModelFilters] for the
 *   taxonomy. The catalog list is computed from
 *   `catalog + installedIds + downloading + filters`; the UI never
 *   re-derives it.
 * @property filteredCatalog The visible list — [catalog] after
 *   [filters] + installed / downloading membership have been applied.
 *   Computed once per state update so the screen renders the same list
 *   the user just produced by tapping a chip.
 * @property lastError Optional transient error message (e.g. "Cannot
 *   select: model not installed") — shown as a snackbar then cleared via
 *   [ModelManagementIntent.DismissError].
 * @property detailsForId If non-null, the bottom sheet for this model's
 *   details is shown.
 */
@Immutable
data class ModelManagementState(
    val catalog: List<LlmModelDescriptor> = emptyList(),
    val installedIds: Set<String> = emptySet(),
    val selectedId: String? = null,
    val downloading: Map<String, PerModelProgress> = emptyMap(),
    val filters: ModelFilters = ModelFilters.DEFAULT,
    val filteredCatalog: List<LlmModelDescriptor> = emptyList(),
    val lastError: String? = null,
    val detailsForId: String? = null,
)
