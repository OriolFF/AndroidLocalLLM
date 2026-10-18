package com.llmlocal.recipekeeper.testing

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.web.JsonLdNode
import com.llmlocal.core.web.PageImage
import com.llmlocal.core.web.PageSnapshot
import com.llmlocal.core.web.ReadableContent
import com.llmlocal.core.web.TextSection
import com.llmlocal.recipekeeper.domain.ClippedRecipe
import com.llmlocal.recipekeeper.domain.RecipeRepository
import com.llmlocal.recipekeeper.domain.RecipeSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal ISO-8601 duration parser standing in for `IsoDuration.toMinutes`
 * (`:core:web`, implemented by WS2). Handles `PT1H30M`, `P0DT0H20M0S`.
 */
val testDurationParser: (String?) -> Int? = parser@{ iso ->
    if (iso.isNullOrBlank()) return@parser null
    val m = Regex("^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$", RegexOption.IGNORE_CASE).matchEntire(iso.trim())
        ?: return@parser null
    val days = m.groupValues[1].toIntOrNull() ?: 0
    val hours = m.groupValues[2].toIntOrNull() ?: 0
    val minutes = m.groupValues[3].toIntOrNull() ?: 0
    (days * 24 * 60 + hours * 60 + minutes).takeIf { it > 0 }
}

/** Loads `src/test/resources/jsonld/<name>.json` as a flattened [JsonLdNode]. */
fun loadJsonLd(name: String): JsonLdNode {
    val stream = checkNotNull(Thread.currentThread().contextClassLoader?.getResourceAsStream("jsonld/$name.json")) {
        "missing fixture jsonld/$name.json"
    }
    val raw = JSONObject(stream.bufferedReader().readText())
    return JsonLdNode(types = typesOf(raw), raw = raw)
}

fun typesOf(raw: JSONObject): List<String> = when (val t = raw.opt("@type")) {
    is String -> listOf(t)
    is JSONArray -> (0 until t.length()).map { t.getString(it) }
    else -> emptyList()
}

fun snapshot(
    url: String = "https://www.example.com/recipes/banana-bread",
    title: String? = "Classic Banana Bread – Example Kitchen",
    jsonLd: List<JsonLdNode> = emptyList(),
    readable: ReadableContent? = null,
    sections: List<TextSection> = emptyList(),
    images: List<PageImage> = emptyList(),
    openGraph: Map<String, String> = emptyMap(),
): PageSnapshot = PageSnapshot(
    url = url,
    title = title,
    siteName = "Example Kitchen",
    jsonLd = jsonLd,
    readable = readable,
    sections = sections,
    images = images,
    openGraph = openGraph,
    capturedAt = 1_700_000_000_000L,
)

fun section(heading: String?, level: Int, text: String, top: Int, images: List<String> = emptyList()): TextSection =
    TextSection(heading = heading, level = level, text = text, imageSrcs = images, topOffset = top)

fun image(src: String, w: Int, h: Int, top: Int, inCard: Boolean = false, natural: Int = 1200): PageImage =
    PageImage(src = src, alt = null, width = w, height = h, naturalWidth = natural, naturalHeight = natural, top = top, inRecipeCard = inCard)

fun readable(text: String, title: String? = null): ReadableContent =
    ReadableContent(title = title, byline = null, excerpt = text.take(120), textContent = text, length = text.length)

class TestDispatcherProvider(dispatcher: TestDispatcher = StandardTestDispatcher()) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

/** In-memory [RecipeRepository] for sink / pipeline tests. */
class FakeRecipeRepository : RecipeRepository {
    private val store = MutableStateFlow<Map<String, ClippedRecipe>>(emptyMap())
    private val deleted = mutableMapOf<String, Long>()
    val upserts = mutableListOf<ClippedRecipe>()

    val recipes: List<ClippedRecipe> get() = store.value.values.toList()

    override fun observeAll(): Flow<List<RecipeSummary>> = store.map { map ->
        map.values.filter { it.id !in deleted }.sortedByDescending { it.clippedAt }.map { it.summary() }
    }

    override fun observe(id: String): Flow<ClippedRecipe?> = store.map { it[id] }

    override suspend fun upsert(recipe: ClippedRecipe) {
        upserts += recipe
        store.value = store.value + (recipe.id to recipe)
    }

    override suspend fun softDelete(id: String) { deleted[id] = System.currentTimeMillis() }

    override suspend fun restore(id: String) { deleted.remove(id) }

    override suspend fun delete(id: String) {
        deleted.remove(id)
        store.value = store.value - id
    }

    override suspend fun purgeDeleted(olderThanMillis: Long): List<String> {
        val ids = deleted.filterValues { it < olderThanMillis }.keys.toList()
        ids.forEach { delete(it) }
        return ids
    }

    override suspend fun setChecked(id: String, checked: Set<Int>) {
        store.value[id]?.let { upsert(it.copy(checkedIngredients = checked)) }
    }

    override fun search(query: String): Flow<List<RecipeSummary>> = observeAll().map { list ->
        list.filter { it.title.contains(query, ignoreCase = true) }
    }

    private fun ClippedRecipe.summary() = RecipeSummary(id, title, sourceHost, totalMinutes, pictures.firstOrNull(), truncated, clippedAt)
}
