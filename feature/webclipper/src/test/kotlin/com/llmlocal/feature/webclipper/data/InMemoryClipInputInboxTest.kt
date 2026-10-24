package com.llmlocal.feature.webclipper.data

import com.google.common.truth.Truth.assertThat
import com.llmlocal.feature.webclipper.domain.input.PendingClipInput
import org.junit.Test

class InMemoryClipInputInboxTest {

    private val inbox = InMemoryClipInputInbox()

    @Test
    fun `starts empty and consume returns null`() {
        assertThat(inbox.pending.value).isNull()
        assertThat(inbox.consume()).isNull()
    }

    @Test
    fun `offer sets pending and consume returns it once`() {
        val images = PendingClipInput.Images(listOf("content://a", "content://b"))
        inbox.offer(images)

        assertThat(inbox.pending.value).isEqualTo(images)
        assertThat(inbox.consume()).isEqualTo(images)
        assertThat(inbox.pending.value).isNull()
        assertThat(inbox.consume()).isNull()
    }

    @Test
    fun `a newer offer replaces an unconsumed one`() {
        inbox.offer(PendingClipInput.Url("https://a.example"))
        inbox.offer(PendingClipInput.Url("https://b.example"))

        assertThat(inbox.consume()).isEqualTo(PendingClipInput.Url("https://b.example"))
    }
}
