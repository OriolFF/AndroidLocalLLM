package com.llmlocal.core.web

import android.webkit.WebView
import com.llmlocal.core.common.result.Outcome

/**
 * Captures the page currently loaded in a [WebView].
 *
 * Implemented over `WebView.evaluateJavascript` (which must run on the UI
 * thread, hence `suspend`): the implementation injects
 * `assets/webclipper/capture.js`, parses the returned JSON on a background
 * dispatcher and maps it to a [PageSnapshot].
 *
 * Implementation lands in WS2 (`WebViewPageCapture`).
 */
interface PageCapture {

    /** Full capture: JSON-LD, Readability, sections, images, Open Graph. */
    suspend fun capture(webView: WebView): Outcome<PageSnapshot>

    /**
     * Cheap count of schema.org `Recipe`-like nodes (JSON-LD + microdata)
     * for the proactive clip-button badge. Runs a ~20-line script only.
     */
    suspend fun quickScanStructured(webView: WebView): Int
}
