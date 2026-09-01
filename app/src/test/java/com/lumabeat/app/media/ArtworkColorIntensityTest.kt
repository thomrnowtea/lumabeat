package com.lumabeat.app.media

import com.lumabeat.app.wiz.LightColor
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkColorIntensityTest {
    @Test
    fun `natural preserves the extracted artwork color exactly`() {
        val color = LightColor(200, 170, 150)

        assertEquals(listOf(color), ArtworkColorIntensity.NATURAL.apply(listOf(color)))
    }

    @Test
    fun `vivid increases saturation without increasing peak brightness`() {
        val adjusted = ArtworkColorIntensity.VIVID.apply(listOf(LightColor(200, 170, 150))).single()

        assertEquals(LightColor(200, 160, 133), adjusted)
    }

    @Test
    fun `neutral white remains neutral at every intensity`() {
        val white = LightColor(255, 255, 255)

        ArtworkColorIntensity.entries.forEach { intensity ->
            assertEquals(white, intensity.apply(listOf(white)).single())
        }
    }
}
