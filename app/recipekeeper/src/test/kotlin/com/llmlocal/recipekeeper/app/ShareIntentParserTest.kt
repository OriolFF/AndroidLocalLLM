package com.llmlocal.recipekeeper.app

import com.google.common.truth.Truth.assertThat
import com.llmlocal.feature.webclipper.domain.input.PendingClipInput
import org.junit.Test

class ShareIntentParserTest {

    private val send = "android.intent.action.SEND"
    private val sendMultiple = "android.intent.action.SEND_MULTIPLE"

    @Test
    fun `single stream uri is an images share`() {
        val result = ShareIntentParser.parse(send, "image/png", listOf("content://a/1"), emptyList(), null)
        assertThat(result).isEqualTo(PendingClipInput.Images(listOf("content://a/1")))
    }

    @Test
    fun `send multiple keeps the senders order`() {
        val uris = listOf("content://a/3", "content://a/1", "content://a/2")
        val result = ShareIntentParser.parse(sendMultiple, "image/*", uris, emptyList(), null)
        assertThat(result).isEqualTo(PendingClipInput.Images(uris))
    }

    @Test
    fun `clip data only uris are used and duplicates with the stream collapse`() {
        assertThat(ShareIntentParser.parse(send, "image/jpeg", emptyList(), listOf("content://a/1"), null))
            .isEqualTo(PendingClipInput.Images(listOf("content://a/1")))
        assertThat(ShareIntentParser.parse(send, "image/jpeg", listOf("content://a/1"), listOf("content://a/1"), null))
            .isEqualTo(PendingClipInput.Images(listOf("content://a/1")))
    }

    @Test
    fun `more uris than the cap are passed through`() {
        val uris = (1..12).map { "content://a/$it" }
        val result = ShareIntentParser.parse(sendMultiple, "image/*", uris, emptyList(), null) as PendingClipInput.Images
        assertThat(result.uris).hasSize(12)
    }

    @Test
    fun `text with a link inside prose opens that link without trailing punctuation`() {
        val result = ShareIntentParser.parse(send, "text/plain", emptyList(), emptyList(), "Try this: https://example.com/soup?a=1, so good.")
        assertThat(result).isEqualTo(PendingClipInput.Url("https://example.com/soup?a=1"))
    }

    @Test
    fun `text without a link is nothing`() {
        assertThat(ShareIntentParser.parse(send, "text/plain", emptyList(), emptyList(), "no link here")).isNull()
        assertThat(ShareIntentParser.parse(send, "text/plain", emptyList(), emptyList(), null)).isNull()
    }

    @Test
    fun `a text share ignores clip data uris and other actions are refused`() {
        assertThat(ShareIntentParser.parse(send, "text/plain", emptyList(), listOf("content://a/1"), "https://a.example/x"))
            .isEqualTo(PendingClipInput.Url("https://a.example/x"))
        assertThat(ShareIntentParser.parse("android.intent.action.MAIN", null, emptyList(), emptyList(), "https://a.example/x")).isNull()
    }
}
