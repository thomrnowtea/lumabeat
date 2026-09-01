package com.lumabeat.app.media

import com.lumabeat.app.wiz.LightColor
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
    fun `keeps white but ignores black and gray when colors are available`() {
        val pixels = buildList {
            repeat(10) { add(argb(255, 255, 255)) }
            repeat(20) { add(argb(15, 15, 15)) }
            repeat(20) { add(argb(130, 130, 130)) }
            repeat(10) { add(argb(220, 30, 40)) }
        }.toIntArray()

        val colors = DominantColorExtractor.extract(pixels)

        assertEquals(2, colors.size)
        assertTrue(colors.any { it.red == it.green && it.green == it.blue })
        assertTrue(colors.any { it.red > it.green && it.red > it.blue })
    }

    @Test
    fun `uses neutral white for fully black or gray artwork`() {
        val pixels = intArrayOf(
            argb(0, 0, 0),
            argb(20, 20, 20),
            argb(130, 130, 130),
        )

        assertEquals(
            listOf(LightColor(255, 255, 255)),
            DominantColorExtractor.extract(pixels),
        )
    }

    @Test
    fun `does not replace the last screen palette with a black frame`() {
        val pixels = intArrayOf(
            argb(0, 0, 0),
            argb(20, 20, 20),
            argb(130, 130, 130),
        )

        assertTrue(
            DominantColorExtractor.extract(pixels, fallbackToWhite = false).isEmpty(),
        )
    }

    @Test
    fun `returns no color for fully transparent artwork`() {
        val pixels = intArrayOf(0x0044AAFF)

        assertTrue(DominantColorExtractor.extract(pixels).isEmpty())
    }

    @Test
    fun `never returns more than three colors`() {
        val pixels = intArrayOf(
            argb(230, 20, 20),
            argb(20, 230, 20),
            argb(20, 20, 230),
            argb(230, 20, 230),
        )

        assertEquals(3, DominantColorExtractor.extract(pixels, maximumColors = 10).size)
    }

    @Test
    fun `does not fill the palette with nearly identical shades`() {
        val pixels = intArrayOf(
            argb(30, 220, 100),
            argb(31, 221, 101),
            argb(32, 222, 102),
        )

        assertEquals(1, DominantColorExtractor.extract(pixels).size)
    }

    @Test
    fun `groups light and dark variants from the same hue family`() {
        val pixels = intArrayOf(
            argb(54, 35, 12),
            argb(120, 94, 12),
            argb(142, 173, 228),
        )

        val colors = DominantColorExtractor.extract(pixels)

        assertEquals(2, colors.size)
        assertTrue(colors.any { it.blue > it.red && it.blue > it.green })
        assertTrue(colors.any { it.red > it.blue && it.green > it.blue })
    }

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}
