package com.llmlocal.core.web

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageGeometryTest {

    @Test
    fun `a picture within the cap is not scaled`() {
        assertThat(ImageGeometry.targetScale(1_079, 2_397, 2_400)).isEqualTo(1.0)
        assertThat(ImageGeometry.sampleSizeFor(1_079, 2_397, 1.0)).isEqualTo(1)
    }

    @Test
    fun `a large photo is scaled to the long edge cap`() {
        val scale = ImageGeometry.targetScale(4_000, 3_000, 2_400)
        assertThat(scale).isWithin(1e-9).of(0.6)
        val (w, h) = ImageGeometry.scaledSize(4_000, 3_000, scale)
        assertThat(w).isEqualTo(2_400)
        assertThat(h).isEqualTo(1_800)
    }

    @Test
    fun `sample size is the largest power of two that keeps the target resolution`() {
        assertThat(ImageGeometry.sampleSizeFor(4_000, 3_000, 0.6)).isEqualTo(1)
        assertThat(ImageGeometry.sampleSizeFor(8_000, 6_000, 0.3)).isEqualTo(2)
        assertThat(ImageGeometry.sampleSizeFor(8_000, 6_000, 0.1)).isEqualTo(8)
        assertThat(ImageGeometry.sampleSizeFor(0, 0, 0.5)).isEqualTo(1)
    }

    @Test
    fun `a tall strip keeps a legible short edge instead of shrinking to the long edge cap`() {
        val scale = ImageGeometry.targetScale(1_080, 10_000, 2_400)
        // short edge stays at 1080 → no scale down by the long-edge rule
        assertThat(scale).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `an enormous strip is bounded by the pixel budget`() {
        val scale = ImageGeometry.targetScale(1_080, 40_000, 2_400)
        val pixels = 1_080.0 * 40_000 * scale * scale
        assertThat(pixels).isAtMost(ImageGeometry.MAX_PIXELS.toDouble() * 1.001)
        assertThat(scale).isLessThan(1.0)
    }

    @Test
    fun `a picture that is not tall yields a single full rectangle`() {
        assertThat(ImageGeometry.tileRects(1_080, 2_400, 2.5)).containsExactly(TileRect(0, 0, 1_080, 2_400))
        assertThat(ImageGeometry.tileRects(1_000, 2_500, 2.5)).containsExactly(TileRect(0, 0, 1_000, 2_500))
    }

    @Test
    fun `a tall strip is cut into viewport shaped overlapping tiles covering it entirely`() {
        val width = 1_000
        val height = 6_000
        val tiles = ImageGeometry.tileRects(width, height, 2.5)

        assertThat(tiles.size).isGreaterThan(2)
        tiles.forEach {
            assertThat(it.width).isEqualTo(width)
            assertThat(it.height).isEqualTo(2_200)
            assertThat(it.top).isAtLeast(0)
            assertThat(it.bottom).isAtMost(height)
        }
        assertThat(tiles.first().top).isEqualTo(0)
        assertThat(tiles.last().bottom).isEqualTo(height)
        tiles.zipWithNext().forEach { (a, b) ->
            assertThat(b.top).isGreaterThan(a.top)
            // neighbours share at least 10 % of a tile (minus rounding)
            assertThat(a.bottom - b.top).isAtLeast((2_200 * 0.10).toInt() - 1)
        }
    }

    @Test
    fun `tile count for the measured eval screenshot shape`() {
        // 1079 x 8000 → 2374-px tiles, 10 % overlap
        val tiles = ImageGeometry.tileRects(1_079, 8_000, 2.5)
        assertThat(tiles.first().top).isEqualTo(0)
        assertThat(tiles.last().bottom).isEqualTo(8_000)
        assertThat(tiles.size).isEqualTo(4)
    }
}
