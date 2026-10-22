package com.llmlocal.recipekeeper.clip.domain

import com.google.common.truth.Truth.assertThat
import com.llmlocal.feature.webclipper.domain.CandidatePayload
import com.llmlocal.feature.webclipper.domain.CandidateSource
import com.llmlocal.feature.webclipper.domain.ClipCandidate
import com.llmlocal.recipekeeper.testing.image
import com.llmlocal.recipekeeper.testing.loadJsonLd
import com.llmlocal.recipekeeper.testing.snapshot
import com.llmlocal.recipekeeper.testing.testDurationParser
import org.junit.Test

class PictureKeysTest {

    @Test
    fun `resized variants of one photo share a key`() {
        val keys = listOf(
            "https://blog.example.com/wp-content/uploads/2024/05/chocolate-brownies.jpg",
            "https://blog.example.com/wp-content/uploads/2024/05/chocolate-brownies-300x200.jpg",
            "https://blog.example.com/wp-content/uploads/2024/05/chocolate-brownies-scaled.jpg",
            "https://blog.example.com/wp-content/uploads/2024/05/chocolate-brownies-1024x683.jpg?ver=2",
            "https://res.example.com/image/upload/w_300/2024/05/chocolate-brownies.jpg",
        ).map(PictureKeys::key)

        assertThat(keys.take(4).toSet()).hasSize(1)
        assertThat(keys.last()).isEqualTo(keys.first().replace("blog.example.com", "res.example.com"))
    }

    @Test
    fun `thumb path segments and generic stems`() {
        assertThat(PictureKeys.key("https://cdn.example.cat/thumbphoto/400/tarta-de-poma.jpg"))
            .isEqualTo(PictureKeys.key("https://cdn.example.cat/tarta-de-poma.jpg"))
        // Two different photos called image.jpg in different folders stay distinct.
        assertThat(PictureKeys.key("https://x.com/a/image.jpg")).isNotEqualTo(PictureKeys.key("https://x.com/b/image.jpg"))
    }

    @Test
    fun `declared widths`() {
        assertThat(PictureKeys.declaredWidth("https://x.com/a-300x200.jpg")).isEqualTo(300)
        assertThat(PictureKeys.declaredWidth("https://x.com/thumbphoto/400/a.jpg")).isEqualTo(400)
        assertThat(PictureKeys.declaredWidth("https://x.com/a.jpg?w=640")).isEqualTo(640)
        assertThat(PictureKeys.declaredWidth("https://x.com/a.jpg")).isNull()
    }

    @Test
    fun `rankPictures keeps one image per stem and prefers the largest`() {
        val config = RecipeKeeperClipConfig("Untitled", testDurationParser)
        val page = snapshot(
            jsonLd = listOf(loadJsonLd("single_recipe")),
            images = listOf(
                image("https://cdn.example.com/up/brownies-300x300.jpg", 300, 300, 10, natural = 300),
                image("https://cdn.example.com/up/brownies-1200x1200.jpg", 600, 600, 20, natural = 1200),
                image("https://cdn.example.com/up/other-dish-photo.jpg", 600, 600, 30, natural = 800),
            ),
        )
        val candidate = ClipCandidate(
            id = "t", title = "t", thumbnailUrl = null, snippet = null, source = CandidateSource.STRUCTURED,
            ingredientCount = 3, totalMinutes = null, payload = CandidatePayload.Structured(page.jsonLd.first()),
        )

        val urls = config.rankPictures(candidate, page, 10).map { it.url }

        assertThat(urls.count { "brownies" in it }).isEqualTo(1)
        assertThat(urls).contains("https://cdn.example.com/up/brownies-1200x1200.jpg")
        assertThat(urls).contains("https://cdn.example.com/up/other-dish-photo.jpg")
    }
}
