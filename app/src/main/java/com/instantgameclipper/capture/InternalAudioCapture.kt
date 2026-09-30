package com.instantgameclipper.capture

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.util.Log
import com.instantgameclipper.codec.AudioEncoder
import com.instantgameclipper.codec.MemoryRingBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class InternalAudioCapture(
    private val ringBuffer: MemoryRingBuffer,
) {
    private val running = AtomicBoolean(false)
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private val encoder = AudioEncoder(ringBuffer) { format ->
        CaptureRuntime.audioFormat = format
    }

    @SuppressLint("MissingPermission")
    fun start(mediaProjection: MediaProjection) {
        stop()

        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AudioEncoder.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        val minBuf = AudioRecord.getMinBufferSize(
            AudioEncoder.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(AudioEncoder.MAX_INPUT_SIZE)

        val audioRecord = runCatching {
            AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBuf * 2)
                .setAudioPlaybackCaptureConfig(config)
                .build()
        }.getOrElse { error ->
            Log.e(TAG, "AudioRecord create failed", error)
            return
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            Log.e(TAG, "AudioRecord not initialized")
            return
        }

        encoder.start()
        audioRecord.startRecording()
        record = audioRecord
        running.set(true)

        val bytesPerUs = AudioEncoder.SAMPLE_RATE * AudioEncoder.CHANNEL_COUNT * 2 / 1_000_000.0
        worker = thread(name = "internal-audio", isDaemon = true) {
            val buf = ByteArray(minBuf)
            var ptsUs = 0L
            while (running.get()) {
                val read = audioRecord.read(buf, 0, buf.size)
                if (read > 0) {
                    encoder.encode(buf, 0, read, ptsUs, endOfStream = false)
                    ptsUs += (read / bytesPerUs).toLong()
                }
            }
            encoder.encode(ByteArray(0), 0, 0, ptsUs, endOfStream = true)
        }
        Log.i(TAG, "internal audio capture started")
    }

    fun stop() {
        running.set(false)
        runCatching { worker?.join(500) }
        worker = null
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        encoder.stop()
    }

    companion object {
        private const val TAG = "InternalAudio"
    }
}
