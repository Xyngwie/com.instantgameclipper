package com.instantgameclipper.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface

class VideoEncoder(
    private val ringBuffer: MemoryRingBuffer,
    private val onOutputFormat: (MediaFormat) -> Unit = {},
) {
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var callbackThread: HandlerThread? = null

    fun start(width: Int, height: Int, bitRate: Int = DEFAULT_BIT_RATE): Surface {
        stop()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SEC)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }

        val thread = HandlerThread("video-encoder").also { it.start() }
        callbackThread = thread

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo,
            ) {
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val copy = ByteArray(info.size)
                    buffer.get(copy)
                    ringBuffer.add(
                        EncodedFrame(
                            kind = EncodedFrame.Kind.VIDEO,
                            data = copy,
                            presentationTimeUs = info.presentationTimeUs,
                            flags = info.flags,
                        ),
                    )
                }
                runCatching { codec.releaseOutputBuffer(index, false) }
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.e(TAG, "encoder error", e)
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.i(TAG, "output format $format")
                onOutputFormat(MediaFormat(format))
            }
        }, Handler(thread.looper))

        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = encoder.createInputSurface()
        encoder.start()

        codec = encoder
        inputSurface = surface
        return surface
    }

    fun stop() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { inputSurface?.release() }
        inputSurface = null
        callbackThread?.quitSafely()
        callbackThread = null
    }

    companion object {
        private const val TAG = "VideoEncoder"
        const val FRAME_RATE = 30
        const val I_FRAME_INTERVAL_SEC = 1
        const val DEFAULT_BIT_RATE = 8_000_000
    }
}
