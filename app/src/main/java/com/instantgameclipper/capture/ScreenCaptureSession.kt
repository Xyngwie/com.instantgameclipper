package com.instantgameclipper.capture

import android.content.Context
import android.content.res.Resources
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.util.Log
import com.instantgameclipper.codec.MemoryRingBuffer
import com.instantgameclipper.codec.VideoEncoder
import kotlin.math.min

class ScreenCaptureSession(
    private val ringBuffer: MemoryRingBuffer = CaptureRuntime.ringBuffer,
) {
    private val encoder = VideoEncoder(ringBuffer)
    private var virtualDisplay: VirtualDisplay? = null
    private var projection: MediaProjection? = null

    fun start(context: Context, mediaProjection: MediaProjection) {
        stop()
        projection = mediaProjection

        val metrics = Resources.getSystem().displayMetrics
        val (width, height) = capTo1080(metrics.widthPixels, metrics.heightPixels)
        val dpi = metrics.densityDpi

        val surface = encoder.start(width, height)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            DISPLAY_NAME,
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null,
        )
        Log.i(TAG, "VirtualDisplay started ${width}x$height dpi=$dpi")
        context.applicationContext
    }

    fun stop() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        encoder.stop()
        projection = null
    }

    private fun capTo1080(rawW: Int, rawH: Int): Pair<Int, Int> {
        val longSide = maxOf(rawW, rawH)
        val shortSide = minOf(rawW, rawH)
        val scale = min(1f, 1920f / longSide)
        val w = align16((shortSide * scale).toInt())
        val h = align16((longSide * scale).toInt())
        return if (rawW >= rawH) h to w else w to h
    }

    private fun align16(value: Int): Int = (value / 16).coerceAtLeast(1) * 16

    companion object {
        private const val TAG = "ScreenCapture"
        private const val DISPLAY_NAME = "InstantGameClipper"
    }
}

object CaptureRuntime {
    val ringBuffer = MemoryRingBuffer()
    val session = ScreenCaptureSession(ringBuffer)
}
