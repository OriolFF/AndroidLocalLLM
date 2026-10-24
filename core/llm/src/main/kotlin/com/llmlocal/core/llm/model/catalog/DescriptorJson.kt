package com.llmlocal.core.llm.model.catalog

import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.core.model.ModelAccelerator
import com.llmlocal.core.model.ModelFamily
import org.json.JSONArray
import org.json.JSONObject

/** JSON codec for the discovered-models cache. Unknown enum values fall back to defaults. */
object DescriptorJson {

    fun encode(descriptors: List<LlmModelDescriptor>): String {
        val array = JSONArray()
        descriptors.forEach { d ->
            array.put(
                JSONObject()
                    .put("id", d.id)
                    .put("displayName", d.displayName)
                    .put("description", d.description)
                    .put("url", d.url)
                    .put("sizeBytes", d.sizeBytes)
                    .put("filename", d.filename)
                    .putOpt("sha256", d.sha256)
                    .put("tags", JSONArray(d.tags))
                    .put("license", d.license)
                    .put("author", d.author)
                    .put("minRamMb", d.minRamMb)
                    .put("family", d.family.name)
                    .put("accelerator", d.accelerator.name)
                    .putOpt("sourceRepo", d.sourceRepo)
                    .putOpt("baseModel", d.baseModel)
                    .putOpt("updatedAt", d.updatedAt)
                    .putOpt("contextTokens", d.contextTokens),
            )
        }
        return array.toString()
    }

    fun decode(json: String): List<LlmModelDescriptor> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = o.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val filename = o.optString("filename").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.optString(it) } }.orEmpty()
            LlmModelDescriptor(
                id = id,
                displayName = o.optString("displayName", id),
                description = o.optString("description"),
                url = url,
                sizeBytes = o.optLong("sizeBytes"),
                filename = filename,
                sha256 = o.optString("sha256").takeIf { it.isNotBlank() },
                tags = tags,
                license = o.optString("license", "Unknown"),
                author = o.optString("author", "Unknown"),
                minRamMb = o.optInt("minRamMb"),
                family = enumOrDefault(o.optString("family"), ModelFamily.GENERAL),
                accelerator = enumOrDefault(o.optString("accelerator"), ModelAccelerator.CPU),
                sourceRepo = o.optString("sourceRepo").takeIf { it.isNotBlank() },
                baseModel = o.optString("baseModel").takeIf { it.isNotBlank() },
                updatedAt = o.optString("updatedAt").takeIf { it.isNotBlank() },
                contextTokens = o.optInt("contextTokens").takeIf { it > 0 },
            )
        }
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
