package com.llmlocal.feature.webclipper

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TruncationHeuristicTest {

    private val nearCap = 40 // tokens

    @Test
    fun `short output is never truncated`() {
        assertThat(TruncationHeuristic.looksTruncated("Title: Soup\nSteps:\n1. Boil the", 1_024)).isFalse()
    }

    @Test
    fun `near the cap without terminal punctuation is truncated`() {
        val text = "Steps:\n" + "1. Stir the pot for a while and keep going with the".padEnd(nearCap * 4, 'x')
        assertThat(TruncationHeuristic.looksTruncated(text, nearCap)).isTrue()
    }

    @Test
    fun `near the cap with a clean ending is not truncated`() {
        val text = "Steps:\n" + "1. Stir the pot for a while and keep going".padEnd(nearCap * 4 - 1, 'x') + "."
        assertThat(TruncationHeuristic.looksTruncated(text, nearCap)).isFalse()
        val header = "Steps:\n" + "x".repeat(nearCap * 4) + "\nNotes:"
        assertThat(TruncationHeuristic.looksTruncated(header, nearCap)).isFalse()
    }

    @Test
    fun `no cap or empty text is never truncated`() {
        assertThat(TruncationHeuristic.looksTruncated("x".repeat(10_000), null)).isFalse()
        assertThat(TruncationHeuristic.looksTruncated("", 10)).isFalse()
    }
}
