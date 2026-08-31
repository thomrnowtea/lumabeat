package com.lumabeat.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class PercussionDetector(
    private val presetProvider: () -> BeatPreset = { BeatPreset.MARCADO },
) {
    private val previousMagnitude = DoubleArray(MAX_ANALYZED_BIN + 1)
    private var averageFlux = INITIAL_AVERAGE
    private var averageBassEnergy = INITIAL_AVERAGE
    private var envelope = 0f
    private var analyzedFrames = 0
    private var cooldownFrames = 0

    fun analyze(samples: ShortArray, sampleCount: Int): AudioLevel {
        val completeWindows = sampleCount / FFT_SIZE
        if (completeWindows == 0) return levelFromEnvelope(envelope, isBeat = false)

        var peakEnvelope = envelope
        var beatDetected = false
        repeat(completeWindows) { windowIndex ->
            val hitStrength = detectHit(samples, windowIndex * FFT_SIZE)
            beatDetected = updateEnvelope(hitStrength) || beatDetected
            peakEnvelope = max(peakEnvelope, envelope)
        }
        return levelFromEnvelope(peakEnvelope, beatDetected)
    }

    private fun detectHit(samples: ShortArray, offset: Int): Float {
        val real = DoubleArray(FFT_SIZE)
        val imaginary = DoubleArray(FFT_SIZE)
        applyHannWindow(samples, offset, real)
        fft(real, imaginary)

        var flux = 0.0
        var bassEnergy = 0.0
        for (bin in 1..MAX_ANALYZED_BIN) {
            val magnitude = sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]) / FFT_SIZE
            flux += (magnitude - previousMagnitude[bin]).coerceAtLeast(0.0)
            if (bin <= MAX_BASS_BIN) bassEnergy += magnitude * magnitude
            previousMagnitude[bin] = magnitude
        }

        val preset = presetProvider()
        val bassOnset = ratioExcess(bassEnergy, averageBassEnergy, preset.bassThreshold)
        val generalOnset = ratioExcess(flux, averageFlux, preset.generalThreshold) *
            preset.generalWeight
        averageFlux = movingAverage(averageFlux, flux)
        averageBassEnergy = movingAverage(averageBassEnergy, bassEnergy)
        analyzedFrames++
        if (analyzedFrames <= WARMUP_FRAMES) return 0f
        return max(bassOnset, generalOnset).toFloat().coerceIn(0f, 1f)
    }

    private fun updateEnvelope(hitStrength: Float): Boolean {
        val preset = presetProvider()
        if (cooldownFrames > 0) {
            cooldownFrames--
            envelope *= preset.releaseMultiplier
            return false
        }
        if (hitStrength < preset.hitGate) {
            envelope *= preset.releaseMultiplier
            return false
        }

        envelope = preset.minimumHitLevel + hitStrength * (1f - preset.minimumHitLevel)
        cooldownFrames = preset.cooldownFrames
        return true
    }

    private fun levelFromEnvelope(value: Float, isBeat: Boolean): AudioLevel {
        val preset = presetProvider()
        val normalized = value.coerceIn(0f, 1f)
        val brightnessRange = preset.peakBrightness - preset.baselineBrightness
        return AudioLevel(
            normalized = normalized,
            brightnessPercent = preset.baselineBrightness + (normalized * brightnessRange).toInt(),
            isBeat = isBeat,
        )
    }

    private fun applyHannWindow(samples: ShortArray, offset: Int, destination: DoubleArray) {
        for (index in destination.indices) {
            val window = 0.5 - 0.5 * cos(2.0 * PI * index / (FFT_SIZE - 1))
            destination[index] = samples[offset + index] / Short.MAX_VALUE.toDouble() * window
        }
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        bitReverse(real, imaginary)
        var size = 2
        while (size <= FFT_SIZE) {
            applyButterflies(real, imaginary, size)
            size = size shl 1
        }
    }

    private fun bitReverse(real: DoubleArray, imaginary: DoubleArray) {
        var reversed = 0
        for (index in 1 until FFT_SIZE) {
            var bit = FFT_SIZE shr 1
            while (reversed and bit != 0) {
                reversed = reversed xor bit
                bit = bit shr 1
            }
            reversed = reversed xor bit
            if (index < reversed) {
                real.swap(index, reversed)
                imaginary.swap(index, reversed)
            }
        }
    }

    private fun applyButterflies(real: DoubleArray, imaginary: DoubleArray, size: Int) {
        val angle = -2.0 * PI / size
        val phaseStepReal = cos(angle)
        val phaseStepImaginary = sin(angle)
        val half = size / 2
        for (start in 0 until FFT_SIZE step size) {
            var phaseReal = 1.0
            var phaseImaginary = 0.0
            for (offset in 0 until half) {
                val even = start + offset
                val odd = even + half
                val oddReal = real[odd] * phaseReal - imaginary[odd] * phaseImaginary
                val oddImaginary = real[odd] * phaseImaginary + imaginary[odd] * phaseReal
                real[odd] = real[even] - oddReal
                imaginary[odd] = imaginary[even] - oddImaginary
                real[even] += oddReal
                imaginary[even] += oddImaginary

                val nextPhaseReal = phaseReal * phaseStepReal - phaseImaginary * phaseStepImaginary
                phaseImaginary = phaseReal * phaseStepImaginary + phaseImaginary * phaseStepReal
                phaseReal = nextPhaseReal
            }
        }
    }

    private fun ratioExcess(value: Double, average: Double, threshold: Double): Double {
        val ratio = value / (average + INITIAL_AVERAGE)
        return ((ratio - threshold) / ONSET_RATIO_RANGE).coerceIn(0.0, 1.0)
    }

    private fun movingAverage(current: Double, value: Double): Double =
        current + (value - current) * AVERAGE_RATE

    private fun DoubleArray.swap(first: Int, second: Int) {
        val temporary = this[first]
        this[first] = this[second]
        this[second] = temporary
    }

    private companion object {
        const val FFT_SIZE = 1_024
        const val SAMPLE_RATE = 44_100
        const val MAX_ANALYZED_FREQUENCY = 4_000
        const val MAX_BASS_FREQUENCY = 220
        const val MAX_ANALYZED_BIN = MAX_ANALYZED_FREQUENCY * FFT_SIZE / SAMPLE_RATE
        const val MAX_BASS_BIN = MAX_BASS_FREQUENCY * FFT_SIZE / SAMPLE_RATE
        const val INITIAL_AVERAGE = 0.000001
        const val AVERAGE_RATE = 0.16
        const val ONSET_RATIO_RANGE = 1.8
        const val WARMUP_FRAMES = 4
    }
}
