package com.llmlocal.feature.modelmanagement.domain

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.model.ModelFamily
import com.llmlocal.feature.modelmanagement.descriptor
import com.llmlocal.feature.modelmanagement.domain.usecase.FilterModelsUseCase
import com.llmlocal.feature.modelmanagement.progress
import org.junit.Test

class FilterModelsUseCaseTest {

    private val gb = 1024L * 1024 * 1024
    private val filter = FilterModelsUseCase()

    private val installed = ModelListing(
        descriptor("a", sizeBytes = 3 * gb, family = ModelFamily.INSTRUCT), installed = true, selected = true, progress = null,
    )
    private val downloading = ModelListing(
        descriptor("b", sizeBytes = gb / 2, tags = listOf("Quantised")), installed = false, selected = false, progress = progress("b"),
    )
    private val available = ModelListing(
        descriptor("c", sizeBytes = gb + gb / 2, family = ModelFamily.INSTRUCT), installed = false, selected = false, progress = null,
    )
    private val all = listOf(installed, downloading, available)

    private fun ids(filters: ModelFilters) = filter(all, filters).map { it.id }

    @Test fun `default filters keep everything`() {
        assertThat(ids(ModelFilters.DEFAULT)).containsExactly("a", "b", "c").inOrder()
    }

    @Test fun `status filters`() {
        assertThat(ids(ModelFilters(status = StatusFilter.INSTALLED))).containsExactly("a")
        assertThat(ids(ModelFilters(status = StatusFilter.DOWNLOADING))).containsExactly("b")
        assertThat(ids(ModelFilters(status = StatusFilter.AVAILABLE))).containsExactly("c")
    }

    @Test fun `family filter`() {
        assertThat(ids(ModelFilters(family = ModelFamily.INSTRUCT))).containsExactly("a", "c")
    }

    @Test fun `size filter`() {
        assertThat(ids(ModelFilters(sizeBucket = SizeBucket.COMPACT))).containsExactly("b")
        assertThat(ids(ModelFilters(sizeBucket = SizeBucket.SMALL))).containsExactly("c")
        assertThat(ids(ModelFilters(sizeBucket = SizeBucket.FULL))).containsExactly("a")
    }

    @Test fun `quant filter is case insensitive on tags`() {
        assertThat(ids(ModelFilters(quant = QuantFilter.QUANTISED))).containsExactly("b")
        assertThat(ids(ModelFilters(quant = QuantFilter.FULL_PRECISION))).containsExactly("a", "c")
    }

    @Test fun `filters compose with AND`() {
        val f = ModelFilters(family = ModelFamily.INSTRUCT, status = StatusFilter.AVAILABLE)
        assertThat(ids(f)).containsExactly("c")
    }
}
