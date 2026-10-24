package com.llmlocal.core.designsystem.color

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class AccentColorsTest {

    private val tomato = 0xFFD9472B.toInt()   // hue ≈ 27
    private val basil = 0xFF3E7D3A.toInt()    // hue ≈ 140
    private val plate = 0xFFEDEDED.toInt()    // near-white neutral
    private val shadow = 0xFF3A3A3A.toInt()   // near-black neutral

    /** A "photo" that is mostly plate with a tomato in the middle and a few basil leaves. */
    private fun soupPhoto(): IntArray {
        val pixels = IntArray(64 * 64)
        for (y in 0 until 64) for (x in 0 until 64) {
            val dx = x - 32
            val dy = y - 32
            pixels[y * 64 + x] = when {
                dx * dx + dy * dy < 18 * 18 -> tomato
                (x + y) % 23 == 0 -> basil
                y > 58 -> shadow
                else -> plate
            }
        }
        return pixels
    }

    @Test
    fun `seeds rank the colourful subject over the neutral background`() {
        val seeds = AccentColors.seeds(soupPhoto())

        assertThat(seeds).isNotEmpty()
        assertThat(hueDistance(AccentColors.hue(seeds.first()), AccentColors.hue(tomato))).isLessThan(15.0)
        // The plate and the shadow are near-grey: Score's chroma cutoff drops them.
        assertThat(seeds.map { AccentColors.hue(it) }.any { hueDistance(it, AccentColors.hue(basil)) < 15.0 }).isTrue()
    }

    @Test
    fun `a greyscale picture still yields its dominant neutral`() {
        val pixels = IntArray(32 * 32) { if (it % 7 == 0) shadow else plate }

        val seeds = AccentColors.seeds(pixels)

        assertThat(seeds).isNotEmpty()
        assertThat(AccentColors.tone(seeds.first())).isGreaterThan(80.0)
    }

    @Test
    fun `empty input gives no seeds`() {
        assertThat(AccentColors.seeds(IntArray(0))).isEmpty()
    }

    @Test
    fun `washed keeps the hue and lands on a text-safe tone`() {
        val light = AccentColors.washed(tomato, dark = false)
        val dark = AccentColors.washed(tomato, dark = true)

        assertThat(AccentColors.tone(light)).isWithin(1.5).of(93.0)
        assertThat(AccentColors.tone(dark)).isWithin(1.5).of(18.0)
        assertThat(hueDistance(AccentColors.hue(light), AccentColors.hue(tomato))).isLessThan(8.0)
        assertThat(hueDistance(AccentColors.hue(dark), AccentColors.hue(tomato))).isLessThan(8.0)
    }

    private fun hueDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }
}
