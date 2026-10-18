package com.llmlocal.core.web

import org.json.JSONArray
import org.json.JSONObject

/**
 * Maps the raw `capture.js` JSON (a [JSONObject] of the IIFE return value)
 * to a [PageSnapshot].
 *
 * Kept separate from [WebViewPageCapture] so it can be unit-tested on the
 * JVM with a real [JSONObject] fixture, without spinning up a `WebView`.
 */
internal object PageSnapshotMapper {

    /**
     * Build a [PageSnapshot] from the IIFE return value. JSON-LD blocks and
     * microdata objects are flattened via [JsonLdExtractor]; sections,
     * images and Open Graph tags are mapped structurally.
     *
     * @param json the object returned by `capture.js`
     * @param capturedAt `System.currentTimeMillis()` value to stamp
     */
    fun fromJson(json: JSONObject, capturedAt: Long): PageSnapshot {
        val jsonLdBlocks: List<String> = json.optJSONArray("jsonLd").toStringList()
        val microdataBlocks: List<String> = json.optJSONArray("microdata").toStringList()

        // Both kinds are fed through the same extractor; microdata nodes
        // are already shaped like JSON-LD by the script.
        val allBlocks = jsonLdBlocks + microdataBlocks
        val jsonLd = JsonLdExtractor.fromScriptBlocks(allBlocks)

        return PageSnapshot(
            url = json.optString("url"),
            title = json.optStringOrNull("title"),
            siteName = json.optStringOrNull("siteName"),
            jsonLd = jsonLd,
            readable = json.optJSONObject("readable")?.let(::readable),
            sections = json.optJSONArray("sections").toObjectList().map(::section),
            images = json.optJSONArray("images").toObjectList().map(::image),
            openGraph = json.optJSONObject("og").toStringMap(),
            capturedAt = capturedAt,
        )
    }

    private fun readable(obj: JSONObject): ReadableContent = ReadableContent(
        title = obj.optStringOrNull("title"),
        byline = obj.optStringOrNull("byline"),
        excerpt = obj.optStringOrNull("excerpt"),
        textContent = obj.optString("textContent"),
        length = obj.optInt("length", obj.optString("textContent").length),
    )

    private fun section(obj: JSONObject): TextSection = TextSection(
        heading = obj.optStringOrNull("heading"),
        level = obj.optInt("level", 0),
        text = obj.optString("text"),
        imageSrcs = obj.optJSONArray("imageSrcs").toStringList(),
        topOffset = obj.optInt("topOffset", 0),
    )

    private fun image(obj: JSONObject): PageImage = PageImage(
        src = obj.optString("src"),
        alt = obj.optStringOrNull("alt"),
        width = obj.optInt("width", 0),
        height = obj.optInt("height", 0),
        naturalWidth = obj.optInt("naturalWidth", 0),
        naturalHeight = obj.optInt("naturalHeight", 0),
        top = obj.optInt("top", 0),
        inRecipeCard = obj.optBoolean("inRecipeCard", false),
    )
}

// ----- JSONObject / JSONArray conveniences ------------------------------------

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val s = optString(key)
    return s.ifBlank { null }
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val out = LinkedHashMap<String, String>()
    keys().forEach { k ->
        val v = opt(k)
        if (v != null && v != JSONObject.NULL) out[k] = v.toString()
    }
    return out
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val v = opt(i)
        if (v != null && v != JSONObject.NULL) out += v.toString()
    }
    return out
}

private fun JSONArray?.toObjectList(): List<JSONObject> {
    if (this == null) return emptyList()
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) {
        optJSONObject(i)?.let(out::add)
    }
    return out
}