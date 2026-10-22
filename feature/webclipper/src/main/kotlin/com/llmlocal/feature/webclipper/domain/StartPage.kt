package com.llmlocal.feature.webclipper.domain

/**
 * What the in-app browser opens when it starts without a page: the library's
 * "Clip a recipe" entry, as opposed to "Open original", which passes a URL.
 *
 * [url] is the page to load, or `null` for [Blank] (the address bar gets
 * focus instead). The default is [Google]; the user changes it in the app's
 * settings screen through [StartPageRepository].
 */
sealed interface StartPage {
    val url: String?

    /** Google's home page. The default. */
    data object Google : StartPage {
        override val url: String = "https://www.google.com/"
    }

    /** No page: the address bar is focused with the keyboard open. */
    data object Blank : StartPage {
        override val url: String? = null
    }

    /** A user-chosen `http(s)` address. */
    data class Custom(override val url: String) : StartPage

    companion object {
        val DEFAULT: StartPage = Google
    }
}
