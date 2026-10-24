package com.llmlocal.feature.modelmanagement.domain.usecase

import com.llmlocal.feature.modelmanagement.domain.DownloadProgressSource
import com.llmlocal.feature.modelmanagement.domain.DownloadWorkSource
import com.llmlocal.feature.modelmanagement.domain.DownloadWorkStatus
import com.llmlocal.feature.modelmanagement.domain.InstalledModelsSource
import com.llmlocal.feature.modelmanagement.domain.ModelCatalogSource
import com.llmlocal.feature.modelmanagement.domain.ModelListing
import com.llmlocal.feature.modelmanagement.domain.ModelSelectionSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Combines catalog, installed set, selection, download progress and job
 * status into one stream of [ModelListing]s.
 *
 * The installed set is re-scanned on start, whenever a download job
 * reaches `SUCCEEDED`, whenever the catalog changes (a discovery may add
 * models whose files are already on disk) and whenever [refresh] emits
 * (manual refresh, after a removal). The first emission shows the catalog
 * with nothing installed so the list renders before the disk scan finishes.
 */
class ObserveModelListingsUseCase(
    private val catalog: ModelCatalogSource,
    private val installed: InstalledModelsSource,
    private val selection: ModelSelectionSource,
    private val progress: DownloadProgressSource,
    private val work: DownloadWorkSource,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(refresh: Flow<Unit> = emptyFlow()): Flow<List<ModelListing>> {
        val succeeded = work.observe()
            .map { statuses -> DownloadWorkStatus.SUCCEEDED in statuses }
            .map { }
        val catalogChanged = catalog.models.map { }
        val installedIds = merge(succeeded, refresh, catalogChanged)
            .onStart { emit(Unit) }
            .mapLatest { installed.installedIds() }
            .onStart { emit(emptySet()) }

        return combine(catalog.models, installedIds, selection.selectedId, progress.progress) { models, ids, selectedId, snapshots ->
            models.map { descriptor ->
                ModelListing(
                    descriptor = descriptor,
                    installed = descriptor.id in ids,
                    selected = descriptor.id == selectedId,
                    progress = snapshots[descriptor.id],
                )
            }
        }
    }
}
