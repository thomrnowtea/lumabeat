package com.lumabeat.app.media

import com.lumabeat.app.wiz.LightColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object DominantColorExtractor {
    fun extract(pixels: IntArray, maximumColors: Int = 3): List<LightColor> {
        if (pixels.isEmpty() || maximumColors <= 0) return emptyList()
        val colorLimit = maximumColors.coerceAtMost(MAX_COLORS)
        val samples = collectSamples(pixels)
        val candidates = samples.buckets.values
            .sortedByDescending(Bucket::score)
            .map(Bucket::averageColor)
        return selectDistinctColors(candidates, colorLimit, samples.hasOpaqueArtwork)
    }

    private fun collectSamples(pixels: IntArray): SampleCollection {
        val buckets = mutableMapOf<Int, Bucket>()
        var hasOpaqueArtwork = false
        pixels.forEach { pixel ->
            if (!isOpaque(pixel)) return@forEach
            hasOpaqueArtwork = true
            val sample = eligibleSample(pixel) ?: return@forEach
            buckets.getOrPut(sample.quantizedKey(), ::Bucket).add(sample)
        }
        return SampleCollection(buckets, hasOpaqueArtwork)
    }

    private fun eligibleSample(pixel: Int): ColorSample? {
        val red = pixel ushr 16 and 0xFF
        val green = pixel ushr 8 and 0xFF
        val blue = pixel and 0xFF
        val value = max(red, max(green, blue))
        val chroma = value - min(red, min(green, blue))
        val isWhite = min(red, min(green, blue)) >= MIN_WHITE_CHANNEL &&
            chroma <= MAX_WHITE_CHROMA
        if (shouldIgnore(value, chroma, isWhite)) return null
        return ColorSample(red, green, blue, chroma)
    }

    private fun shouldIgnore(value: Int, chroma: Int, isWhite: Boolean): Boolean =
        !isWhite && (value < MIN_VALUE || chroma < MIN_CHROMA)

    private fun isOpaque(pixel: Int): Boolean = (pixel ushr 24 and 0xFF) >= MIN_ALPHA

    private fun selectDistinctColors(
        candidates: List<LightColor>,
        colorLimit: Int,
        hasOpaqueArtwork: Boolean,
    ): List<LightColor> {
        val selected = mutableListOf<LightColor>()
        candidates.forEach { candidate ->
            if (selected.all { existing -> arePerceptuallyDistinct(existing, candidate) }) {
                selected += candidate
            }
            if (selected.size == colorLimit) return selected
        }
        return selected.ifEmpty {
            if (hasOpaqueArtwork) listOf(NEUTRAL_WHITE) else emptyList()
        }
    }

    private fun colorDistance(first: LightColor, second: LightColor): Int =
        abs(first.red - second.red) + abs(first.green - second.green) + abs(first.blue - second.blue)

    private fun arePerceptuallyDistinct(first: LightColor, second: LightColor): Boolean {
        if (first.isWhite() || second.isWhite()) return first.isWhite() != second.isWhite()
        if (colorDistance(first, second) < MIN_COLOR_DISTANCE) return false
        val directHueDistance = abs(first.hueDegrees() - second.hueDegrees())
        val circularHueDistance = min(directHueDistance, FULL_HUE_CIRCLE - directHueDistance)
        return circularHueDistance >= MIN_HUE_DISTANCE_DEGREES
    }

    private fun LightColor.isWhite(): Boolean = min(red, min(green, blue)) >= MIN_WHITE_CHANNEL &&
        max(red, max(green, blue)) - min(red, min(green, blue)) <= MAX_WHITE_CHROMA

    private fun LightColor.hueDegrees(): Double {
        val highest = max(red, max(green, blue)).toDouble()
        val lowest = min(red, min(green, blue)).toDouble()
        val chroma = highest - lowest
        if (chroma == 0.0) return 0.0
        val rawHue = when (highest.toInt()) {
            red -> (green - blue) / chroma
            green -> (blue - red) / chroma + 2.0
            else -> (red - green) / chroma + 4.0
        } * HUE_SECTOR_DEGREES
        return (rawHue + FULL_HUE_CIRCLE) % FULL_HUE_CIRCLE
    }

    private data class SampleCollection(
        val buckets: Map<Int, Bucket>,
        val hasOpaqueArtwork: Boolean,
    )

    private data class ColorSample(
        val red: Int,
        val green: Int,
        val blue: Int,
        val chroma: Int,
    ) {
        fun quantizedKey(): Int = (red shr QUANTIZATION_SHIFT shl 8) or
            (green shr QUANTIZATION_SHIFT shl 4) or
            (blue shr QUANTIZATION_SHIFT)
    }

    private class Bucket {
        private var count = 0
        private var redTotal = 0L
        private var greenTotal = 0L
        private var blueTotal = 0L
        private var chromaTotal = 0L

        fun add(sample: ColorSample) {
            count++
            redTotal += sample.red
            greenTotal += sample.green
            blueTotal += sample.blue
            chromaTotal += sample.chroma
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
    private const val MIN_WHITE_CHANNEL = 235
    private const val MAX_WHITE_CHROMA = 12
    private const val MIN_COLOR_DISTANCE = 115
    private const val MIN_HUE_DISTANCE_DEGREES = 28.0
    private const val HUE_SECTOR_DEGREES = 60.0
    private const val FULL_HUE_CIRCLE = 360.0
    private const val AVERAGE_CHROMA_WEIGHT = 40L
    private const val MAX_COLORS = 3
    private val NEUTRAL_WHITE = LightColor(255, 255, 255)
}
