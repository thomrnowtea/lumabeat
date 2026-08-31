package com.lumabeat.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class PercussionDetectorTest {
    @Test
    fun silenceKeepsBaselineBrightness() {
        val detector = PercussionDetector()
        val silence = ShortArray(WINDOW_SIZE)

        warmUp(detector, silence)

        assertEquals(18, detector.analyze(silence, silence.size).brightnessPercent)
    }

    @Test
    fun bassTransientCreatesPulseAndThenDecays() {
        val detector = PercussionDetector()
        val silence = ShortArray(WINDOW_SIZE)
        warmUp(detector, silence)

        val pulse = detector.analyze(bassDrumBurst(), WINDOW_SIZE)
        val decayed = repeatAnalysis(detector, silence, 8)

        assertTrue("expected a percussion event", pulse.isBeat)
        assertTrue("expected a marked pulse, got ${pulse.brightnessPercent}", pulse.brightnessPercent >= 75)
        assertTrue("expected a fast decay, got $decayed", decayed <= 22)
    }

    @Test
    fun sustainedToneDoesNotRetriggerEveryWindow() {
        val detector = PercussionDetector()
        val silence = ShortArray(WINDOW_SIZE)
        val tone = sustainedTone()
        warmUp(detector, silence)

        val first = detector.analyze(tone, tone.size)
        repeatAnalysis(detector, tone, 14)
        val settled = detector.analyze(tone, tone.size)

        assertTrue(first.isBeat)
        assertTrue("steady audio should not look like a new hit", !settled.isBeat)
    }

    @Test
    fun repeatedBassTransientRetriggersAfterFastDecay() {
        val detector = PercussionDetector()
        val silence = ShortArray(WINDOW_SIZE)
        warmUp(detector, silence)

        val first = detector.analyze(bassDrumBurst(), WINDOW_SIZE)
        repeatAnalysis(detector, silence, 8)
        val second = detector.analyze(bassDrumBurst(), WINDOW_SIZE)

        assertTrue(first.isBeat)
        assertTrue(second.isBeat)
    }

    @Test
    fun intensePresetCreatesMoreContrastThanSoftPreset() {
        val silence = ShortArray(WINDOW_SIZE)
        val soft = PercussionDetector { BeatPreset.SUAVE }
        val intense = PercussionDetector { BeatPreset.INTENSO }
        warmUp(soft, silence)
        warmUp(intense, silence)

        val softPulse = soft.analyze(bassDrumBurst(), WINDOW_SIZE).brightnessPercent
        val intensePulse = intense.analyze(bassDrumBurst(), WINDOW_SIZE).brightnessPercent

        assertTrue(intensePulse - BeatPreset.INTENSO.baselineBrightness > softPulse - BeatPreset.SUAVE.baselineBrightness)
    }

    private fun repeatAnalysis(
        detector: PercussionDetector,
        samples: ShortArray,
        repetitions: Int,
    ): Int {
        var brightness = 0
        repeat(repetitions) {
            brightness = detector.analyze(samples, samples.size).brightnessPercent
        }
        return brightness
    }

    private fun warmUp(detector: PercussionDetector, samples: ShortArray) {
        repeat(5) { detector.analyze(samples, samples.size) }
    }

    private fun bassDrumBurst(): ShortArray = ShortArray(WINDOW_SIZE) { index ->
        val time = index / SAMPLE_RATE.toDouble()
        val envelope = exp(-18.0 * time)
        (sin(2.0 * PI * 90.0 * time) * envelope * Short.MAX_VALUE * 0.9).toInt().toShort()
    }

    private fun sustainedTone(): ShortArray = ShortArray(WINDOW_SIZE) { index ->
        val time = index / SAMPLE_RATE.toDouble()
        (sin(2.0 * PI * 90.0 * time) * Short.MAX_VALUE * 0.55).toInt().toShort()
    }

    private companion object {
        const val WINDOW_SIZE = 1_024
        const val SAMPLE_RATE = 44_100
    }
}
