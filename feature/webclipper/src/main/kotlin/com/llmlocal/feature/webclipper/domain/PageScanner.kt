package com.llmlocal.feature.webclipper.domain

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.web.PageSnapshot

/**
 * Page capture bound to *the* clipper WebView, so nothing above `data/`
 * touches `android.webkit` (which keeps the use cases unit-testable on the JVM).
 */
interface PageScanner {

    /** Cheap structured-data count for the proactive clip-button badge. */
    suspend fun quickScanStructured(): Int

    /** Full capture of the page currently shown. */
    suspend fun capture(): Outcome<PageSnapshot>
}
