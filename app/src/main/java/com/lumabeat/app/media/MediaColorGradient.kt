package com.lumabeat.app.media

import com.lumabeat.app.wiz.LightColor
import kotlin.math.roundToInt

object MediaColorGradient {
    fun colorAt(
        palette: List<LightColor>,
        elapsedMillis: Long,
        segmentDurationMillis: Long,
        transitionFrom: LightColor? = null,
    ): LightColor? {
        if (palette.isEmpty()) return null
        require(segmentDurationMillis > 0) { "Segment duration must be positive." }

        val safeElapsedMillis = elapsedMillis.coerceAtLeast(0L)
        if (transitionFrom != null && safeElapsedMillis < segmentDurationMillis) {
            return interpolate(transitionFrom, palette.first(), safeElapsedMillis, segmentDurationMillis)
        }
        if (palette.size == 1) return palette.first()

        val paletteElapsedMillis = if (transitionFrom == null) {
            safeElapsedMillis
        } else {
            safeElapsedMillis - segmentDurationMillis
        }
        val cycleDuration = segmentDurationMillis * palette.size
        val cyclePosition = paletteElapsedMillis % cycleDuration
        val fromIndex = (cyclePosition / segmentDurationMillis).toInt()
        val fraction = (cyclePosition % segmentDurationMillis).toDouble() / segmentDurationMillis
        val from = palette[fromIndex]
        val to = palette[(fromIndex + 1) % palette.size]

        return interpolate(from, to, fraction)
    }

    private fun interpolate(
        from: LightColor,
        to: LightColor,
        elapsedMillis: Long,
        durationMillis: Long,
    ): LightColor = interpolate(from, to, elapsedMillis.toDouble() / durationMillis)

    private fun interpolate(from: LightColor, to: LightColor, fraction: Double): LightColor = LightColor(
        red = interpolate(from.red, to.red, fraction),
        green = interpolate(from.green, to.green, fraction),
        blue = interpolate(from.blue, to.blue, fraction),
    )

    private fun interpolate(from: Int, to: Int, fraction: Double): Int =
        (from + (to - from) * fraction).roundToInt().coerceIn(0, 255)
}
