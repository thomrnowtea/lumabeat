package com.lumabeat.app.media

import com.lumabeat.app.wiz.LightColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaColorGradientTest {
    private val palette = listOf(
        LightColor(240, 40, 20),
        LightColor(40, 200, 80),
        LightColor(20, 60, 240),
    )

    @Test
    fun `returns null for an empty palette`() {
        assertNull(MediaColorGradient.colorAt(emptyList(), 0L, SEGMENT_MILLIS))
    }

    @Test
    fun `keeps a single palette color stable`() {
        val color = LightColor(12, 34, 56)

        assertEquals(color, MediaColorGradient.colorAt(listOf(color), 25_000L, SEGMENT_MILLIS))
    }

    @Test
    fun `interpolates halfway between adjacent colors`() {
        assertEquals(
            LightColor(140, 120, 50),
            MediaColorGradient.colorAt(palette, SEGMENT_MILLIS / 2, SEGMENT_MILLIS),
        )
    }

    @Test
    fun `passes through every palette color and loops smoothly`() {
        assertEquals(palette[0], MediaColorGradient.colorAt(palette, 0L, SEGMENT_MILLIS))
        assertEquals(palette[1], MediaColorGradient.colorAt(palette, SEGMENT_MILLIS, SEGMENT_MILLIS))
        assertEquals(palette[2], MediaColorGradient.colorAt(palette, SEGMENT_MILLIS * 2, SEGMENT_MILLIS))
        assertEquals(palette[0], MediaColorGradient.colorAt(palette, SEGMENT_MILLIS * 3, SEGMENT_MILLIS))
    }

    @Test
    fun `blends from the displayed color when the palette changes`() {
        val displayedColor = LightColor(20, 20, 20)

        assertEquals(
            displayedColor,
            MediaColorGradient.colorAt(palette, 0L, SEGMENT_MILLIS, displayedColor),
        )
        assertEquals(
            LightColor(130, 30, 20),
            MediaColorGradient.colorAt(
                palette,
                SEGMENT_MILLIS / 2,
                SEGMENT_MILLIS,
                displayedColor,
            ),
        )
        assertEquals(
            palette.first(),
            MediaColorGradient.colorAt(palette, SEGMENT_MILLIS, SEGMENT_MILLIS, displayedColor),
        )
    }

    private companion object {
        const val SEGMENT_MILLIS = 5_000L
    }
}
