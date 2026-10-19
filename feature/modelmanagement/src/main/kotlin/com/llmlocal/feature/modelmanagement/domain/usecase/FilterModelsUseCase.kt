package com.llmlocal.feature.modelmanagement.domain.usecase

import com.llmlocal.feature.modelmanagement.domain.ModelFilters
import com.llmlocal.feature.modelmanagement.domain.ModelListing
import com.llmlocal.feature.modelmanagement.domain.QuantFilter
import com.llmlocal.feature.modelmanagement.domain.StatusFilter

/**
 * Pure filter over [ModelListing]s. Filters compose with **AND**:
 *
 *  1. Status — `ALL` keeps everything; `INSTALLED` keeps installed;
 *     `AVAILABLE` keeps models neither installed nor downloading;
 *     `DOWNLOADING` keeps models with a running download.
 *  2. Family — single-select; `null` means all families.
 *  3. Size — single-select; `null` means all sizes.
 *  4. Quantisation — derived from descriptor tags; `ALL` means either.
 */
class FilterModelsUseCase {
    operator fun invoke(listings: List<ModelListing>, filters: ModelFilters): List<ModelListing> =
        listings.filter { l ->
            val d = l.descriptor
            val statusOk = when (filters.status) {
                StatusFilter.ALL -> true
                StatusFilter.INSTALLED -> l.installed
                StatusFilter.AVAILABLE -> !l.installed && !l.downloading
                StatusFilter.DOWNLOADING -> l.downloading
            }
            val familyOk = filters.family == null || d.family == filters.family
            val sizeOk = filters.sizeBucket == null ||
                ModelFilters.bucketFor(d.sizeBytes) == filters.sizeBucket
            val quantOk = when (filters.quant) {
                QuantFilter.ALL -> true
                QuantFilter.QUANTISED -> ModelFilters.isQuantised(d)
                QuantFilter.FULL_PRECISION -> !ModelFilters.isQuantised(d)
            }
            statusOk && familyOk && sizeOk && quantOk
        }
}
