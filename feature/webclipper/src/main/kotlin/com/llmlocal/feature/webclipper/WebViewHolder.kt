package com.llmlocal.feature.webclipper

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps one [WebView] alive across navigation (browser → extraction → back)
 * so the page is not reloaded. Created with the **application** context,
 * never an Activity; the browser screen attaches it via `AndroidView` and
 * the holder follows the Activity lifecycle through a `LifecycleEventObserver`
 * (`onPause` / `onResume`) and `saveState` / `restoreState` on process death.
 *
 * Settings follow research §B: JavaScript + DOM storage on (needed for
 * capture and for most cooking sites), file / content access off, safe
 * browsing on, mixed content never allowed, no `addJavascriptInterface`.
 * `shouldOverrideUrlLoading` keeps `http(s)` in the WebView and hands
 * `intent:` / `mailto:` / `tel:` / other schemes to the system. When the
 * renderer dies the WebView is recreated and [generation] bumps so the
 * screen re-attaches the new instance.
 *
 * Registered as a Koin `single`.
 */
class WebViewHolder(private val appContext: Context) {

    /** WebView events the browser screen forwards to its ViewModel. */
    interface Listener {
        fun onPageStarted(url: String)
        fun onProgress(progress: Int)
        fun onPageFinished(url: String, title: String?)
        fun onHistoryChanged(canGoBack: Boolean, canGoForward: Boolean)
        fun onScrolled(deltaY: Int)
        fun onLoadError()
    }

    /** Set by the screen while it is attached; `null` otherwise. */
    @Volatile
    var listener: Listener? = null

    private var webView: ClipperWebView? = null

    private val _generation = MutableStateFlow(0)

    /** Incremented each time a fresh WebView replaces a dead one. */
    val generation: StateFlow<Int> = _generation

    /** URL currently loaded, or `null` on a blank WebView (incl. before first [acquire]). */
    val currentUrl: String?
        get() = webView?.url?.takeUnless { it.isBlank() || it == "about:blank" }

    /** `document.title` of the current page, if any. */
    val currentTitle: String?
        get() = webView?.title

    val canGoBack: Boolean get() = webView?.canGoBack() == true
    val canGoForward: Boolean get() = webView?.canGoForward() == true

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> webView?.onResume()
            Lifecycle.Event.ON_PAUSE -> webView?.onPause()
            else -> Unit
        }
    }

    /** The shared WebView, created lazily on first access (main thread). */
    fun acquire(): WebView = webView ?: create().also { webView = it }

    /** Detaches the WebView from any previous parent so `AndroidView` can adopt it. */
    fun acquireDetached(): WebView = acquire().also { view ->
        (view.parent as? ViewGroup)?.removeView(view)
    }

    fun loadUrl(url: String) {
        acquire().loadUrl(url)
    }

    fun reload() = webView?.reload()
    fun stopLoading() = webView?.stopLoading()
    fun goBack() = webView?.goBack()
    fun goForward() = webView?.goForward()

    /** Follows [owner]'s pause / resume; call [unbindLifecycle] on dispose. */
    fun bindLifecycle(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(lifecycleObserver)
    }

    fun unbindLifecycle(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(lifecycleObserver)
    }

    /** Writes the WebView's back/forward list + current page into [bundle]. */
    fun saveState(bundle: Bundle) {
        bundle.clear()
        webView?.saveState(bundle)
    }

    /** Restores a [saveState] bundle; only meaningful on a blank WebView. */
    fun restoreState(bundle: Bundle): Boolean {
        if (bundle.isEmpty) return false
        return acquire().restoreState(bundle) != null
    }

    /** Destroys the WebView and drops the reference. */
    fun destroy() {
        webView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.stopLoading()
            view.destroy()
        }
        webView = null
    }

    private fun recreate() {
        destroy()
        webView = create()
        _generation.value = _generation.value + 1
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): ClipperWebView = ClipperWebView(appContext).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
        }
        // Safe Browsing is API 26+ on the platform; androidx.webkit gates it by WebView feature instead.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(settings, true)
        }
        settings.apply {
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        onScrollDelta = { dy -> listener?.onScrolled(dy) }
        webViewClient = ClipperWebViewClient()
        webChromeClient = ClipperChromeClient()
    }

    private inner class ClipperWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return false
            handOffToSystem(uri)
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            listener?.onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            listener?.onPageFinished(url, view.title)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            listener?.onHistoryChanged(view.canGoBack(), view.canGoForward())
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) listener?.onLoadError()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            val crashed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detail.didCrash() else null
            Log.w(TAG, "WebView renderer gone (crash=$crashed); recreating")
            if (view === webView) recreate()
            return true
        }
    }

    private inner class ClipperChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            listener?.onProgress(newProgress)
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            val url = view.url ?: return
            if (view.progress == 100) listener?.onPageFinished(url, title)
        }
    }

    private fun handOffToSystem(uri: Uri) {
        val intent = runCatching {
            if (uri.scheme.equals("intent", ignoreCase = true)) {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
        }.getOrNull() ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
            .onFailure { Log.i(TAG, "No activity for ${uri.scheme}: ${it.message}") }
    }

    /** WebView subclass that reports scroll deltas for FAB expand/collapse. */
    private class ClipperWebView(context: Context) : WebView(context) {
        var onScrollDelta: ((Int) -> Unit)? = null

        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
            super.onScrollChanged(l, t, oldl, oldt)
            onScrollDelta?.invoke(t - oldt)
        }
    }

    private companion object {
        const val TAG = "WebViewHolder"
    }
}
