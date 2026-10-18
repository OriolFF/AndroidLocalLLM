package com.llmlocal.feature.webclipper

import android.content.Context
import android.webkit.WebView

/**
 * Keeps one [WebView] alive across navigation (browser → extraction → back)
 * so the page is not reloaded. Created with the **application** context,
 * never an Activity; the browser screen attaches it via `AndroidView` and
 * the holder follows the Activity lifecycle through a `LifecycleEventObserver`
 * (`onPause` / `onResume`) and `saveState` / `restoreState` on process death.
 *
 * Implementation lands in WS4. Registered as a Koin `single`.
 */
class WebViewHolder(private val appContext: Context) {

    /** The shared WebView, created lazily on first access (main thread). */
    fun acquire(): WebView = TODO("WS4")

    /** Destroys the WebView and drops the reference. */
    fun destroy() {
        TODO("WS4")
    }
}
