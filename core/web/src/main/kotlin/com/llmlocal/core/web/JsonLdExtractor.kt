package com.llmlocal.core.web

/**
 * Pure-Kotlin JSON-LD flattening: parses each `<script type="application/ld+json">`
 * block (tolerating HTML comments / CDATA and malformed blocks), expands
 * top-level arrays and `@graph`, normalises `@type` to a list and follows
 * `mainEntity`.
 *
 * Implementation + fixture tests land in WS2.
 */
object JsonLdExtractor {

    /** @param blocks raw text of every JSON-LD script block on the page. */
    fun fromScriptBlocks(blocks: List<String>): List<JsonLdNode> = TODO("WS2")
}
