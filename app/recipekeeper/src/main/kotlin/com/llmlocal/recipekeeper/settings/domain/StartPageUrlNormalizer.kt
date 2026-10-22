package com.llmlocal.recipekeeper.settings.domain

import java.net.URI

private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

/**
 * Turns what the user typed into the settings "custom address" field into a
 * loadable `http(s)` URL, or `null` when it cannot be one.
 *
 * Trims, prepends `https://` when no scheme was typed, then requires an
 * `http`/`https` scheme and a host with at least one dot (`google.com`,
 * not `google`). The result is the parsed URI's string form.
 */
fun normalizeStartPageUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if (SCHEME_PREFIX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host ?: return null
    if ('.' !in host || host.startsWith('.') || host.endsWith('.')) return null
    return uri.toString()
}
