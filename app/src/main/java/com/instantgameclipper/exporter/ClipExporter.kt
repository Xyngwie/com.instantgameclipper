package com.instantgameclipper.exporter

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.instantgameclipper.capture.CaptureRuntime
import com.instantgameclipper.codec.EncodedFrame
import com.instantgameclipper.codec.MemoryRingBuffer
import java.nio.ByteBuffer

class ClipExporter(
    private val context: Context,
    private val ringBuffer: MemoryRingBuffer = CaptureRuntime.ringBuffer,
) {
    fun exportLast30Seconds(): Result<Uri> = runCatching {
        val frames = ringBuffer.snapshotTrailing(MemoryRingBuffer.DEFAULT_CLIP_US)
        val videoSamples = frames.filter {
            it.kind == EncodedFrame.Kind.VIDEO && !it.isConfig && it.data.isNotEmpty()
        }
        val audioSamples = frames.filter {
            it.kind == EncodedFrame.Kind.AUDIO && !it.isConfig && it.data.isNotEmpty()
        }
        require(videoSamples.isNotEmpty()) { "ring buffer has no video" }

        val videoFormat = buildVideoFormat(frames)
        val audioFormat = CaptureRuntime.audioFormat

        val name = "IGC_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/InstantGameClipper",
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")

        try {
            resolver.openFileDescriptor(uri, "w")?.use { pfd ->
                val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val videoTrack = muxer.addTrack(videoFormat)
                val audioTrack = if (audioFormat != null && audioSamples.isNotEmpty()) {
                    muxer.addTrack(MediaFormat(audioFormat))
                } else {
                    -1
                }
                muxer.start()

                writeTrack(muxer, videoTrack, videoSamples)
                if (audioTrack >= 0) writeTrack(muxer, audioTrack, audioSamples)

                muxer.stop()
                muxer.release()
            } ?: error("openFileDescriptor failed")

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    fun share(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "クリップを共有"))
    }

    private fun writeTrack(
        muxer: MediaMuxer,
        track: Int,
        samples: List<EncodedFrame>,
    ) {
        val info = MediaCodec.BufferInfo()
        val basePts = samples.first().presentationTimeUs
        samples.forEach { frame ->
            info.offset = 0
            info.size = frame.data.size
            info.presentationTimeUs = (frame.presentationTimeUs - basePts).coerceAtLeast(0L)
            info.flags = frame.flags
            muxer.writeSampleData(track, ByteBuffer.wrap(frame.data), info)
        }
    }

    private fun buildVideoFormat(frames: List<EncodedFrame>): MediaFormat {
        val stored = CaptureRuntime.videoFormat
        if (stored != null) return MediaFormat(stored)

        val config = frames.firstOrNull { it.kind == EncodedFrame.Kind.VIDEO && it.isConfig }
            ?: error("missing codec config (SPS/PPS)")
        return MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(config.data))
        }
    }

    companion object {
        const val TAG = "ClipExporter"
        fun log(result: Result<Uri>) {
            result
                .onSuccess { Log.i(TAG, "exported $it") }
                .onFailure { Log.e(TAG, "export failed", it) }
        }
    }
}
