package com.lumabeat.app.media

import com.lumabeat.app.wiz.LightColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object DominantColorExtractor {
    fun extract(pixels: IntArray, maximumColors: Int = 3): List<LightColor> {
        if (pixels.isEmpty() || maximumColors <= 0) return emptyList()
        val buckets = mutableMapOf<Int, Bucket>()
        pixels.forEach { pixel ->
            val alpha = pixel ushr 24 and 0xFF
            if (alpha < MIN_ALPHA) return@forEach
            val red = pixel ushr 16 and 0xFF
            val green = pixel ushr 8 and 0xFF
            val blue = pixel and 0xFF
            val value = max(red, max(green, blue))
            val chroma = value - min(red, min(green, blue))
            if (value < MIN_VALUE || chroma < MIN_CHROMA) return@forEach
            val key = (red shr QUANTIZATION_SHIFT shl 8) or
                (green shr QUANTIZATION_SHIFT shl 4) or
                (blue shr QUANTIZATION_SHIFT)
            buckets.getOrPut(key, ::Bucket).add(red, green, blue, chroma)
        }
        val candidates = buckets.values
            .sortedByDescending(Bucket::score)
            .map { it.averageColor().boosted() }
        val selected = mutableListOf<LightColor>()
        candidates.forEach { candidate ->
                if (selected.all { existing -> colorDistance(existing, candidate) >= MIN_COLOR_DISTANCE }) {
                    selected += candidate
                }
                if (selected.size == maximumColors) return selected
        }
        candidates.filterNot(selected::contains).forEach { candidate ->
            selected += candidate
            if (selected.size == maximumColors) return selected
        }
        return selected
    }

    private fun LightColor.boosted(): LightColor {
        val highest = max(red, max(green, blue)).coerceAtLeast(1)
        val scale = TARGET_VALUE.toDouble() / highest
        return LightColor(
            red = (red * scale).toInt().coerceIn(0, 255),
            green = (green * scale).toInt().coerceIn(0, 255),
            blue = (blue * scale).toInt().coerceIn(0, 255),
        )
    }

    private fun colorDistance(first: LightColor, second: LightColor): Int =
        abs(first.red - second.red) + abs(first.green - second.green) + abs(first.blue - second.blue)

    private class Bucket {
        private var count = 0
        private var redTotal = 0L
        private var greenTotal = 0L
        private var blueTotal = 0L
        private var chromaTotal = 0L

        fun add(red: Int, green: Int, blue: Int, chroma: Int) {
            count++
            redTotal += red
            greenTotal += green
            blueTotal += blue
            chromaTotal += chroma
        }

        fun score(): Long = count.toLong() * (AVERAGE_CHROMA_WEIGHT + chromaTotal / count)

        fun averageColor(): LightColor = LightColor(
            red = (redTotal / count).toInt(),
            green = (greenTotal / count).toInt(),
            blue = (blueTotal / count).toInt(),
        )
    }

    private const val QUANTIZATION_SHIFT = 4
    private const val MIN_ALPHA = 180
    private const val MIN_VALUE = 42
    private const val MIN_CHROMA = 22
    private const val MIN_COLOR_DISTANCE = 115
    private const val TARGET_VALUE = 245
    private const val AVERAGE_CHROMA_WEIGHT = 40L
}
