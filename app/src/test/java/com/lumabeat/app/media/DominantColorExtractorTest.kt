package com.lumabeat.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DominantColorExtractorTest {
    @Test
    fun `returns the three most common distinct vivid colors`() {
        val pixels = buildList {
            repeat(40) { add(argb(230, 30, 40)) }
            repeat(30) { add(argb(25, 210, 80)) }
            repeat(20) { add(argb(35, 70, 225)) }
            repeat(100) { add(argb(5, 5, 8)) }
        }.toIntArray()

        val colors = DominantColorExtractor.extract(pixels)

        assertEquals(3, colors.size)
        assertTrue(colors[0].red > colors[0].green && colors[0].red > colors[0].blue)
        assertTrue(colors[1].green > colors[1].red && colors[1].green > colors[1].blue)
        assertTrue(colors[2].blue > colors[2].red && colors[2].blue > colors[2].green)
    }

    @Test
    fun `ignores transparent dark and neutral pixels`() {
        val pixels = intArrayOf(
            argb(255, 255, 255),
            argb(20, 20, 20),
            0x0044AAFF,
        )

        assertTrue(DominantColorExtractor.extract(pixels).isEmpty())
    }

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}
