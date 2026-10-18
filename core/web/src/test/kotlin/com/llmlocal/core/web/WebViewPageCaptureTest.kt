package com.llmlocal.core.web

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The WebView itself cannot run on the JVM; this covers the script
 * assembly that decides what `evaluateJavascript` receives. The assembled
 * script is exercised for real by `tools/capture-harness/harness.mjs`.
 */
class WebViewPageCaptureTest {

    private val readability = "function Readability(doc, options) {}\nif (typeof module === \"object\") { module.exports = Readability; }"
    private val readerable = "function isProbablyReaderable(doc) { return true; }"
    private val capture = "// capture.js — header comment\n(function () {\n  return { url: document.URL };\n})();\n"

    @Test
    fun `wraps readability and the capture iife in one outer function`() {
        val script = WebViewPageCapture.assembleCaptureScript(readability, readerable, capture)

        assertThat(script).startsWith("(function () {\n")
        assertThat(script).endsWith("})();")
        assertThat(script).contains(readability)
        assertThat(script).contains(readerable)
        assertThat(script.indexOf(readability)).isLessThan(script.indexOf(readerable))
        assertThat(script.indexOf(readerable)).isLessThan(script.indexOf("return ("))
    }

    @Test
    fun `return opens a parenthesis on the same line so leading comments cannot trigger ASI`() {
        val script = WebViewPageCapture.assembleCaptureScript(readability, readerable, capture)

        assertThat(script).contains("return (\n// capture.js — header comment\n(function () {")
    }

    @Test
    fun `trailing semicolon and whitespace of the iife are stripped inside the parenthesis`() {
        val script = WebViewPageCapture.assembleCaptureScript(readability, readerable, capture)

        assertThat(script).contains("})()\n);\n})();")
        assertThat(script).doesNotContain("})();\n);")
    }
}
