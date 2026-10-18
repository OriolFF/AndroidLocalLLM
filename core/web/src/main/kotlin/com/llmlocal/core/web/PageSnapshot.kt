package com.llmlocal.core.web

import org.json.JSONObject

/**
 * Everything `:core:web` captures from the page currently shown in the
 * WebView. Brand-free: the consuming `WebClipConfig<T>` decides what in
 * here is a "recipe", a "product", an "event", …
 *
 * @property url the page URL at capture time
 * @property title `document.title`
 * @property siteName Open Graph / Readability site name when available
 * @property jsonLd flattened JSON-LD nodes: arrays and `@graph` expanded,
 *   `@type` normalised to a list
 * @property readable Readability.js output, or `null` when the page was too
 *   large to run it (size guard) or produced nothing
 * @property sections headings `h1`–`h3` with the text under them, for
 *   Tier B (plain-text) regioning
 * @property images every displayed `<img>` that passed the size filter,
 *   with layout metadata for picture ranking
 * @property openGraph `og:*` / `twitter:*` meta tags
 * @property capturedAt `System.currentTimeMillis()` at capture
 */
data class PageSnapshot(
    val url: String,
    val title: String?,
    val siteName: String?,
    val jsonLd: List<JsonLdNode>,
    val readable: ReadableContent?,
    val sections: List<TextSection>,
    val images: List<PageImage>,
    val openGraph: Map<String, String>,
    val capturedAt: Long,
)

/**
 * One flattened JSON-LD node.
 *
 * @property types `@type` as a list (`"Recipe"` and `["Recipe", "Thing"]`
 *   both end up as a list)
 * @property raw the node itself; callers read fields with the `org.json`
 *   API. Equality on this class is reference-based for [raw].
 */
data class JsonLdNode(
    val types: List<String>,
    val raw: JSONObject,
)

/**
 * A heading plus the text that follows it until the next heading.
 *
 * @property heading heading text, `null` for leading text before any heading
 * @property level 1–3 (`h1`–`h3`)
 * @property text visible text under the heading (capped by the capture script)
 * @property imageSrcs absolute image URLs that appear inside the section
 * @property topOffset vertical document offset of the heading, in CSS px
 */
data class TextSection(
    val heading: String?,
    val level: Int,
    val text: String,
    val imageSrcs: List<String>,
    val topOffset: Int,
)

/**
 * A displayed image with its layout metadata.
 *
 * @property src absolute URL (resolved from `currentSrc` / `srcset` / lazy attrs)
 * @property alt `alt` text, if any
 * @property width displayed width in CSS px
 * @property height displayed height in CSS px
 * @property naturalWidth intrinsic width
 * @property naturalHeight intrinsic height
 * @property top vertical document offset in CSS px
 * @property inRecipeCard whether the image sits inside a known recipe-card
 *   container (`[itemtype*="Recipe"]`, `.wprm-recipe`, …)
 */
data class PageImage(
    val src: String,
    val alt: String?,
    val width: Int,
    val height: Int,
    val naturalWidth: Int,
    val naturalHeight: Int,
    val top: Int,
    val inRecipeCard: Boolean,
)

/**
 * Readability.js output for the main article of the page.
 *
 * @property textContent plain text of the readable article
 * @property length `textContent.length` as reported by Readability
 */
data class ReadableContent(
    val title: String?,
    val byline: String?,
    val excerpt: String?,
    val textContent: String,
    val length: Int,
)
