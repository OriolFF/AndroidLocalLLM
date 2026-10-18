package com.llmlocal.core.web

import android.content.Context
import android.webkit.WebView
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.common.result.runCatchingOutcome
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [PageCapture] over `WebView.evaluateJavascript`.
 *
 * Lifecycle:
 * 1. The first call reads `assets/webclipper/{Readability.js,
 *    Readability-readerable.js, capture.js, quickscan.js}` on
 *    [DispatcherProvider.io] and caches them for the process lifetime.
 * 2. [capture] hops to [DispatcherProvider.main] (the only thread
 *    `evaluateJavascript` accepts) and evaluates **one** script that wraps
 *    Readability and the `capture.js` IIFE in an outer function (see
 *    [assembleCaptureScript]), so the page's global scope is left alone
 *    and the result is the IIFE's object, single-encoded as JSON by
 *    Chromium. The JSON is parsed and mapped on [DispatcherProvider.default].
 * 3. [quickScanStructured] evaluates `quickscan.js` as-is; it returns a
 *    number, which arrives as its decimal string.
 *
 * `evaluateJavascript` reports JS `undefined`/`null` as the four-character
 * string `"null"`; both are unwrapped to a Kotlin `null` and surface as an
 * [Outcome.Failure] (capture) or `0` (quick scan).
 *
 * Cancellation: the continuation is cancellable, so a cancelled caller
 * stops waiting immediately; the WebView callback that fires later is a
 * no-op because the continuation has already completed.
 */
class WebViewPageCapture(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : PageCapture {

    private class Scripts(val capture: String, val quickScan: String)

    private val scriptsCache = AtomicReference<Scripts?>(null)

    private suspend fun scripts(): Scripts {
        scriptsCache.get()?.let { return it }
        return withContext(dispatchers.io) {
            val assets = context.assets
            fun read(name: String) = assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
            Scripts(
                capture = assembleCaptureScript(
                    readability = read(READABILITY_ASSET),
                    readerable = read(READERABLE_ASSET),
                    capture = read(CAPTURE_ASSET),
                ),
                quickScan = read(QUICKSCAN_ASSET),
            )
        }.also { scriptsCache.compareAndSet(null, it) }
    }

    override suspend fun capture(webView: WebView): Outcome<PageSnapshot> {
        val script = scripts().capture
        val raw = withContext(dispatchers.main) { evaluate(webView, script) }
            ?: return Outcome.Failure(IllegalStateException("capture.js returned null"))
        return withContext(dispatchers.default) {
            runCatchingOutcome {
                PageSnapshotMapper.fromJson(JSONObject(raw), capturedAt = System.currentTimeMillis())
            }
        }
    }

    override suspend fun quickScanStructured(webView: WebView): Int {
        val script = scripts().quickScan
        val raw = withContext(dispatchers.main) { evaluate(webView, script) } ?: return 0
        return raw.trim().toDoubleOrNull()?.toInt() ?: 0
    }

    /**
     * Evaluate [script] in [webView] and suspend for the result. Must be
     * called on the main thread. Returns `null` for a JS `undefined`/`null`.
     */
    private suspend fun evaluate(webView: WebView, script: String): String? =
        suspendCancellableCoroutine { cont ->
            try {
                webView.evaluateJavascript(script) { result ->
                    if (!cont.isActive) return@evaluateJavascript
                    cont.resume(result?.takeUnless { it == "null" })
                }
            } catch (t: Throwable) {
                if (cont.isActive) cont.resumeWithException(t)
            }
        }

    companion object {
        private const val ASSET_DIR = "webclipper"
        private const val READABILITY_ASSET = "Readability.js"
        private const val READERABLE_ASSET = "Readability-readerable.js"
        private const val CAPTURE_ASSET = "capture.js"
        private const val QUICKSCAN_ASSET = "quickscan.js"

        /**
         * Wrap Readability + `capture.js` in one outer function so that
         * Readability's top-level declarations stay out of the page's
         * global scope and the whole script evaluates to the object the
         * `capture.js` IIFE returns.
         */
        internal fun assembleCaptureScript(readability: String, readerable: String, capture: String): String =
            buildString {
                append("(function () {\n")
                append(readability).append("\n;\n")
                append(readerable).append("\n;\n")
                // `return (` on one line: a line terminator right after
                // `return` would trigger ASI and return undefined, and
                // capture.js starts with comment lines.
                append("return (\n").append(capture.trim().removeSuffix(";")).append("\n);\n")
                append("})();")
            }
    }
}
