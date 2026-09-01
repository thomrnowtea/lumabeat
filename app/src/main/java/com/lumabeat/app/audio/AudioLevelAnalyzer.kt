package com.lumabeat.app.audio

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

data class AudioLevel(
    val normalized: Float,
    val brightnessPercent: Int,
    val isBeat: Boolean = false,
    val signalPresent: Boolean = true,
    val inputRms: Float = 0f,
    val captureSource: String = "playback_capture",
)

class AudioLevelAnalyzer(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun levels(
        projectionResultCode: Int,
        projectionData: Intent,
        presetProvider: () -> BeatPreset,
    ): Flow<AudioLevel> = flow {
        check(projectionResultCode == Activity.RESULT_OK) {
            "System audio sharing was not approved."
        }
        val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
        val projection = requireNotNull(
            projectionManager.getMediaProjection(projectionResultCode, projectionData),
        ) { "Android did not provide playback audio capture access." }
        val projectionActive = AtomicBoolean(true)
        var recorder: AudioRecord? = null
        val projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                projectionActive.set(false)
                runCatching { recorder?.stop() }
            }
        }
        try {
            projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
            val captureConfiguration = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val minimumBufferBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            check(minimumBufferBytes > 0) { "Android could not allocate an audio capture buffer." }
            val audioRecord = AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minimumBufferBytes, CAPTURE_SAMPLES * Short.SIZE_BYTES))
                .setAudioPlaybackCaptureConfig(captureConfiguration)
                .build()
            recorder = audioRecord
            check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                "Android did not initialize playback audio capture."
            }

            val samples = ShortArray(CAPTURE_SAMPLES)
            val detector = PercussionDetector(presetProvider)
            audioRecord.startRecording()
            check(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Android did not start playback audio capture."
            }
            while (currentCoroutineContext().isActive && projectionActive.get()) {
                val sampleCount = audioRecord.read(
                    samples,
                    0,
                    samples.size,
                    AudioRecord.READ_BLOCKING,
                )
                check(sampleCount >= 0) {
                    "Playback audio capture stopped with status $sampleCount."
                }
                if (sampleCount == 0) continue
                val inputRms = calculateRms(samples, sampleCount)
                val detectedLevel = detector.analyze(samples, sampleCount)
                val signalPresent = inputRms >= SIGNAL_THRESHOLD_RMS
                emit(
                    detectedLevel.copy(
                        normalized = if (signalPresent) detectedLevel.normalized else 0f,
                        isBeat = detectedLevel.isBeat && signalPresent,
                        signalPresent = signalPresent,
                        inputRms = inputRms,
                    ),
                )
            }
        } finally {
            runCatching { recorder?.stop() }
            recorder?.release()
            runCatching { projection.unregisterCallback(projectionCallback) }
            runCatching { projection.stop() }
        }
    }.flowOn(Dispatchers.IO).conflate()

    private fun calculateRms(samples: ShortArray, sampleCount: Int): Float {
        var sumSquares = 0.0
        repeat(sampleCount) { index ->
            val normalized = samples[index] / Short.MAX_VALUE.toDouble()
            sumSquares += normalized * normalized
        }
        return sqrt(sumSquares / sampleCount).toFloat()
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CAPTURE_SAMPLES = 1_024
        const val SIGNAL_THRESHOLD_RMS = 0.001f
    }
}
