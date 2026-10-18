package com.llmlocal.core.web

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Pure-Kotlin JSON-LD flattening: parses each `<script type="application/ld+json">`
 * block (tolerating HTML comments / CDATA and malformed blocks), expands
 * top-level arrays and `@graph`, normalises `@type` to a list and follows
 * `mainEntity`.
 *
 * The output is a flat list of [JsonLdNode]s: each node is the raw
 * `JSONObject` of the resolved object with `@type` guaranteed to be a
 * `List<String>` on the model side. Microdata-style synthetic objects
 * produced by `capture.js` (already shaped like a Recipe JSON-LD node)
 * pass through the same pipeline.
 */
object JsonLdExtractor {

    /**
     * Parse and flatten every JSON-LD script block.
     *
     * @param blocks raw text of every JSON-LD script block on the page
     *   (and/or the synthetic microdata objects already shaped as JSON).
     *   The extractor tolerates: HTML comments (`<!-- … -->`), CDATA
     *   wrappers, leading/trailing whitespace, and individual blocks that
     *   fail to parse (those are dropped, the rest still flows).
     */
    fun fromScriptBlocks(blocks: List<String>): List<JsonLdNode> {
        val out = mutableListOf<JsonLdNode>()
        for (raw in blocks) {
            val cleaned = clean(raw) ?: continue
            val parsed = parseLenient(cleaned) ?: continue
            collectFromValue(parsed, out)
        }
        return out
    }

    // ----- internal helpers --------------------------------------------------

    /**
     * Unwrap a BOM, an HTML comment (`<!-- … -->`) and a CDATA section
     * (`<![CDATA[ … ]]>`, with or without the `//` guards) around the JSON,
     * in that order, and trim. Returns `null` when nothing is left.
     */
    internal fun clean(raw: String): String? {
        var s = raw.trim().removePrefix("\uFEFF").trim()
        s = s.removePrefix("<!--").removeSuffix("-->").trim()
        s = s.removePrefix("//<![CDATA[").removePrefix("<![CDATA[").removeSuffix("//]]>").removeSuffix("]]>").trim()
        return s.takeIf { it.isNotEmpty() }
    }

    /** Try to parse as a JSONArray or JSONObject; return null on failure. */
    internal fun parseLenient(text: String): Any? {
        return try {
            when (text.firstOrNull()) {
                '[' -> JSONArray(text)
                '{' -> JSONObject(text)
                else -> null
            }
        } catch (_: JSONException) {
            null
        }
    }

    /** Walk a parsed value (Array, Object, primitive) and add every node. */
    private fun collectFromValue(value: Any?, out: MutableList<JsonLdNode>) {
        when (value) {
            is JSONArray -> for (i in 0 until value.length()) {
                collectFromValue(value.opt(i), out)
            }
            is JSONObject -> collectFromObject(value, out)
            // primitives and nulls yield no node.
        }
    }

    /**
     * Add [obj] (and every entry in its `@graph`, and its `mainEntity`) to
     * [out], normalising `@type` to a [List]<[String]>.
     */
    private fun collectFromObject(
        obj: JSONObject,
        out: MutableList<JsonLdNode>,
    ) {
        // Recurse into @graph first (adds every node in the graph).
        obj.optJSONArray("@graph")?.let { graph ->
            for (i in 0 until graph.length()) {
                collectFromValue(graph.opt(i), out)
            }
        }
        // Follow mainEntity.
        obj.opt("mainEntity")?.let { main ->
            collectFromValue(main, out)
        }
        // Add this object itself as a node when it has a @type.
        if (obj.has("@type")) {
            out += JsonLdNode(types = readTypes(obj), raw = obj)
        }
    }

    /**
     * Read `@type` as a list of bare type names. A single string, an array
     * of strings, or null are all accepted; non-string entries are
     * dropped. Namespaced forms (`https://schema.org/Recipe`,
     * `http://schema.org/Recipe`, `schema:Recipe`) are normalised to the
     * bare name so consumers can match on `"Recipe"`.
     */
    internal fun readTypes(obj: JSONObject): List<String> = when (val t: Any? = obj.opt("@type")) {
        is String -> listOf(normaliseType(t))
        is JSONArray -> (0 until t.length()).mapNotNull { i -> (t.opt(i) as? String)?.let(::normaliseType) }
        else -> emptyList()
    }.filter { it.isNotEmpty() }

    private val SCHEMA_PREFIX = Regex("""^(?:https?://schema\.org/|schema:)""", RegexOption.IGNORE_CASE)

    private fun normaliseType(type: String): String = type.trim().replace(SCHEMA_PREFIX, "")
}
