package com.llmlocal.feature.webclipper.data

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.web.PageCapture
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.feature.webclipper.domain.PageScanner

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
