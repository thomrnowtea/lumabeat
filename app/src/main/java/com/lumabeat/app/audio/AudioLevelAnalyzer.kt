package com.lumabeat.app.audio

import android.annotation.SuppressLint
import android.media.audiofx.Visualizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

data class AudioLevel(
    val normalized: Float,
    val brightnessPercent: Int,
    val isBeat: Boolean = false,
    val signalPresent: Boolean = true,
    val inputRms: Float = 0f,
    val captureSource: String = "output_mix",
)

class AudioLevelAnalyzer {
    @SuppressLint("MissingPermission")
    fun levels(presetProvider: () -> BeatPreset): Flow<AudioLevel> = flow {
        val visualizer = Visualizer(OUTPUT_MIX_AUDIO_SESSION).apply {
            captureSize = Visualizer.getCaptureSizeRange().last()
            scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
            enabled = true
        }
        val waveform = ByteArray(visualizer.captureSize)
        val samples = ShortArray(visualizer.captureSize)
        val detector = PercussionDetector(presetProvider)
        try {
            while (currentCoroutineContext().isActive) {
                val status = visualizer.getWaveForm(waveform)
                check(status == Visualizer.SUCCESS) {
                    "Android did not allow access to the system audio mix."
                }
                convertWaveform(waveform, samples)
                val inputRms = calculateRms(samples)
                val detectedLevel = detector.analyze(samples, samples.size)
                emit(
                    detectedLevel.copy(
                        normalized = if (inputRms >= SIGNAL_THRESHOLD_RMS) {
                            detectedLevel.normalized
                        } else {
                            0f
                        },
                        isBeat = detectedLevel.isBeat && inputRms >= SIGNAL_THRESHOLD_RMS,
                        signalPresent = inputRms >= SIGNAL_THRESHOLD_RMS,
                        inputRms = inputRms,
                    ),
                )
                delay(ANALYSIS_INTERVAL_MS)
            }
        } finally {
            runCatching { visualizer.enabled = false }
            visualizer.release()
        }
    }.flowOn(Dispatchers.Default).conflate()

    private fun convertWaveform(waveform: ByteArray, samples: ShortArray) {
        waveform.indices.forEach { index ->
            val centeredSample = (waveform[index].toInt() and 0xFF) - UNSIGNED_BYTE_CENTER
            samples[index] = (centeredSample shl BYTE_TO_SHORT_SHIFT).toShort()
        }
    }

    private fun calculateRms(samples: ShortArray): Float {
        var sumSquares = 0.0
        samples.forEach { sample ->
            val normalized = sample / Short.MAX_VALUE.toDouble()
            sumSquares += normalized * normalized
        }
        return kotlin.math.sqrt(sumSquares / samples.size).toFloat()
    }

    private companion object {
        // A 50 ms poll can entirely miss the short attack of a kick or snare. The
        // captured window is about 23 ms at 44.1 kHz, so polling every 20 ms keeps
        // adjacent windows effectively continuous without increasing FFT size.
        const val ANALYSIS_INTERVAL_MS = 20L
        const val SIGNAL_THRESHOLD_RMS = 0.001f
        const val OUTPUT_MIX_AUDIO_SESSION = 0
        const val UNSIGNED_BYTE_CENTER = 128
        const val BYTE_TO_SHORT_SHIFT = 8
    }
}
