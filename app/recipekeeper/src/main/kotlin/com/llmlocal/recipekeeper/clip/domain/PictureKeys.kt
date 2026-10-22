package com.llmlocal.recipekeeper.clip.domain

/**
 * Identity of a picture across its resized variants, so the same photo
 * offered as `foo-300x200.jpg`, `foo-scaled.jpg`, `/thumbphoto/400/foo.jpg`
 * and `foo.jpg?w=600` counts once.
 */
internal object PictureKeys {

    private val sizeSegment = Regex("^(?:thumbs?|thumbnails?|thumbphoto|thumbmobi|small|medium|large|crop-\\d+x\\d+|\\d+x\\d+|\\d+|[a-z]{1,3}_\\d+(?:[,_][a-z]{1,3}_\\d+)*)$", RegexOption.IGNORE_CASE)
    private val extension = Regex("\\.[A-Za-z0-9]{2,5}$")
    private val stemSuffix = Regex("(?:-\\d+x\\d+|-scaled|-\\d+x\\d+-scaled|_\\d+x\\d+|-e\\d{10,})+$", RegexOption.IGNORE_CASE)
    private val dimensions = Regex("[-_](\\d{2,4})x(\\d{2,4})(?=[-_.]|$)")

    /** A generic stem (`image`, `photo`, `1`) is not unique across a site; the full path is then kept. */
    private const val MIN_DISTINCT_STEM = 8

    fun key(url: String): String {
        val clean = TextCleaner.urlKey(url).lowercase()
        val schemeLess = clean.substringAfter("://")
        val host = schemeLess.substringBefore('/')
        val segments = schemeLess.substringAfter('/', "").split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return clean
        val file = segments.last()
        val dirs = segments.dropLast(1).filterNot { sizeSegment.matches(it) }
        val stem = file.replace(extension, "").replace(stemSuffix, "")
        return if (stem.length >= MIN_DISTINCT_STEM) "$host/$stem" else "$host/" + (dirs + stem).joinToString("/")
    }

    private val pathWidth = Regex("/(?:thumbphoto|thumbs?|crop-)/?(\\d{2,4})(?:x\\d+)?(?=/)|[/,_]w_(\\d{2,4})")
    private val queryWidth = Regex("(?:^|&)(?:w|width)=(\\d{2,4})")

    /**
     * Width declared by the URL itself (`-300x200`, `/thumbphoto/400/`,
     * `w_300`, `?w=600`), or `null` when it carries none (the unsuffixed original).
     */
    fun declaredWidth(url: String): Int? {
        val clean = TextCleaner.urlKey(url)
        dimensions.find(clean)?.let { return it.groupValues[1].toIntOrNull() }
        pathWidth.find(clean)?.let { m -> return m.groupValues.drop(1).firstNotNullOfOrNull { it.toIntOrNull() } }
        return queryWidth.find(url.substringAfter('?', "").substringBefore('#'))?.groupValues?.get(1)?.toIntOrNull()
    }
}
