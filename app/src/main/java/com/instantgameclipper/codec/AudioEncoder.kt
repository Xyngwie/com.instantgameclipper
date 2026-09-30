package com.instantgameclipper.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer

class AudioEncoder(
    private val ringBuffer: MemoryRingBuffer,
    private val onOutputFormat: (MediaFormat) -> Unit = {},
) {
    private var codec: MediaCodec? = null
    private val bufferInfo = MediaCodec.BufferInfo()

    fun start(sampleRate: Int = SAMPLE_RATE, channelCount: Int = CHANNEL_COUNT) {
        stop()
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channelCount,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        codec = encoder
    }

    fun encode(pcm: ByteArray, offset: Int, size: Int, ptsUs: Long, endOfStream: Boolean) {
        val encoder = codec ?: return
        val inIndex = encoder.dequeueInputBuffer(10_000)
        if (inIndex >= 0) {
            val input = encoder.getInputBuffer(inIndex)
            input?.clear()
            if (size > 0 && input != null) {
                val write = minOf(size, input.remaining())
                input.put(pcm, offset, write)
            }
            val flags = if (endOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            encoder.queueInputBuffer(inIndex, 0, size.coerceAtLeast(0), ptsUs, flags)
        }
        drain(encoder)
    }

    fun stop() {
        val encoder = codec ?: return
        runCatching { drain(encoder) }
        runCatching { encoder.stop() }
        runCatching { encoder.release() }
        codec = null
    }

    private fun drain(encoder: MediaCodec) {
        while (true) {
            val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    onOutputFormat(MediaFormat(encoder.outputFormat))
                }
                outIndex >= 0 -> {
                    val buffer: ByteBuffer? = encoder.getOutputBuffer(outIndex)
                    if (buffer != null && bufferInfo.size > 0) {
                        buffer.position(bufferInfo.offset)
                        buffer.limit(bufferInfo.offset + bufferInfo.size)
                        val copy = ByteArray(bufferInfo.size)
                        buffer.get(copy)
                        ringBuffer.add(
                            EncodedFrame(
                                kind = EncodedFrame.Kind.AUDIO,
                                data = copy,
                                presentationTimeUs = bufferInfo.presentationTimeUs,
                                flags = bufferInfo.flags,
                            ),
                        )
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                else -> return
            }
        }
    }

    companion object {
        private const val TAG = "AudioEncoder"
        const val SAMPLE_RATE = 44_100
        const val CHANNEL_COUNT = 2
        const val BIT_RATE = 128_000
        const val MAX_INPUT_SIZE = 4096 * 4
    }
}
