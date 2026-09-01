package com.lumabeat.app.media

import com.lumabeat.app.wiz.LightColor
import kotlin.math.max
import kotlin.math.roundToInt

enum class ArtworkColorIntensity(
    val label: String,
    private val saturationMultiplier: Double,
) {
    NATURAL("Natural", 1.0),
    VIVID("Vivid", 1.35),
    BOLD("Bold", 1.7),
    ;

    fun apply(colors: List<LightColor>): List<LightColor> = colors.map(::apply)

    private fun apply(color: LightColor): LightColor {
        if (saturationMultiplier == 1.0) return color
        val highest = max(color.red, max(color.green, color.blue))
        if (highest == 0 || color.red == color.green && color.green == color.blue) return color
        return LightColor(
            red = saturateChannel(color.red, highest),
            green = saturateChannel(color.green, highest),
            blue = saturateChannel(color.blue, highest),
        )
    }

    private fun saturateChannel(channel: Int, highest: Int): Int =
        (highest - (highest - channel) * saturationMultiplier).roundToInt().coerceIn(0, 255)
}
