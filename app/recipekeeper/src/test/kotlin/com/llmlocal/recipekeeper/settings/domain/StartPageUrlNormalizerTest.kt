package com.llmlocal.recipekeeper.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StartPageUrlNormalizerTest {

    @Test
    fun `adds https to a bare host`() {
        assertThat(normalizeStartPageUrl("google.com")).isEqualTo("https://google.com")
        assertThat(normalizeStartPageUrl("  www.bbcgoodfood.com/recipes ")).isEqualTo("https://www.bbcgoodfood.com/recipes")
    }

    @Test
    fun `keeps an explicit http or https scheme`() {
        assertThat(normalizeStartPageUrl("http://example.com/")).isEqualTo("http://example.com/")
        assertThat(normalizeStartPageUrl("HTTPS://Example.com/a?b=c")).isEqualTo("HTTPS://Example.com/a?b=c")
    }

    @Test
    fun `rejects blanks, other schemes, spaces and undotted hosts`() {
        assertThat(normalizeStartPageUrl("")).isNull()
        assertThat(normalizeStartPageUrl("   ")).isNull()
        assertThat(normalizeStartPageUrl("ftp://example.com")).isNull()
        assertThat(normalizeStartPageUrl("javascript://alert(1)")).isNull()
        assertThat(normalizeStartPageUrl("not a url")).isNull()
        assertThat(normalizeStartPageUrl("google")).isNull()
        assertThat(normalizeStartPageUrl("https://")).isNull()
    }
}
