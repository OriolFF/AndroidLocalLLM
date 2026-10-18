package com.llmlocal.feature.webclipper.capture

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.web.PageCapture
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.WebViewHolder

/**
 * [PageCapture] bound to *the* clipper WebView, so the browser ViewModel
 * never touches `android.webkit` (which keeps it unit-testable on the JVM).
 */
interface PageScanner {

    /** Cheap structured-data count for the proactive clip-button badge. */
    suspend fun quickScanStructured(): Int

    /** Full capture of the page currently shown. */
    suspend fun capture(): Outcome<PageSnapshot>
}

/** Production [PageScanner]: `:core:web`'s [PageCapture] over [WebViewHolder]'s WebView. */
class WebViewPageScanner(
    private val pageCapture: PageCapture,
    private val holder: WebViewHolder,
) : PageScanner {

    override suspend fun quickScanStructured(): Int =
        pageCapture.quickScanStructured(holder.acquire())

    override suspend fun capture(): Outcome<PageSnapshot> =
        pageCapture.capture(holder.acquire())
}
