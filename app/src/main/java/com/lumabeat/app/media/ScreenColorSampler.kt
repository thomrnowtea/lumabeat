package com.lumabeat.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import com.lumabeat.app.wiz.LightColor
import kotlin.math.max
import kotlin.math.roundToInt

class ScreenColorSampler(
    context: Context,
    projection: MediaProjection,
) : AutoCloseable {
    private val captureSize = context.captureSize()
    private val imageReader = ImageReader.newInstance(
        captureSize.width,
        captureSize.height,
        PixelFormat.RGBA_8888,
        IMAGE_BUFFER_COUNT,
    )
    private val virtualDisplay: VirtualDisplay = try {
        requireNotNull(
            projection.createVirtualDisplay(
                DISPLAY_NAME,
                captureSize.width,
                captureSize.height,
                context.resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null,
            ),
        ) { "Android did not create the player color capture display." }
    } catch (error: Throwable) {
        imageReader.close()
        throw error
    }

    fun sample(): List<LightColor>? {
        val image = imageReader.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes.firstOrNull() ?: return null
            val paddedWidth = captureSize.width +
                (plane.rowStride - plane.pixelStride * captureSize.width) / plane.pixelStride
            val bitmap = Bitmap.createBitmap(
                paddedWidth,
                captureSize.height,
                Bitmap.Config.ARGB_8888,
            )
            try {
                plane.buffer.rewind()
                bitmap.copyPixelsFromBuffer(plane.buffer)
                val pixels = IntArray(captureSize.width * captureSize.height)
                bitmap.getPixels(
                    pixels,
                    0,
                    captureSize.width,
                    0,
                    0,
                    captureSize.width,
                    captureSize.height,
                )
                DominantColorExtractor.extract(pixels, fallbackToWhite = false)
            } finally {
                bitmap.recycle()
            }
        } finally {
            image.close()
        }
    }

    override fun close() {
        virtualDisplay.release()
        imageReader.close()
    }

    private fun Context.captureSize(): CaptureSize {
        val metrics = resources.displayMetrics
        val longestEdge = max(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        val scale = minOf(1f, MAX_CAPTURE_EDGE / longestEdge.toFloat())
        return CaptureSize(
            width = (metrics.widthPixels * scale).roundToInt().coerceAtLeast(1),
            height = (metrics.heightPixels * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private data class CaptureSize(val width: Int, val height: Int)

    private companion object {
        const val DISPLAY_NAME = "LumaBeat player colors"
        const val MAX_CAPTURE_EDGE = 320
        const val IMAGE_BUFFER_COUNT = 2
    }
}
