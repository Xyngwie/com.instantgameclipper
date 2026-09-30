package com.instantgameclipper.codec

import java.util.ArrayDeque

/**
 * Thread-safe 40-second RAM queue. No file I/O.
 *
 * Eviction drops oldest packets until:
 * - span is <= [capacityUs]
 * - the remaining head is a video keyframe (or the buffer is empty)
 */
class MemoryRingBuffer(
    private val capacityUs: Long = DEFAULT_CAPACITY_US,
) {
    private val lock = Any()
    private val frames = ArrayDeque<EncodedFrame>()
    private var bytes: Long = 0

    fun add(frame: EncodedFrame) {
        synchronized(lock) {
            frames.addLast(frame)
            bytes += frame.data.size
            evictLocked()
        }
    }

    fun snapshot(): List<EncodedFrame> {
        synchronized(lock) {
            return frames.toList()
        }
    }

    /** Last [durationUs] ending at the newest frame, aligned to the preceding video keyframe. */
    fun snapshotTrailing(durationUs: Long = DEFAULT_CLIP_US): List<EncodedFrame> {
        synchronized(lock) {
            if (frames.isEmpty()) return emptyList()
            val endUs = frames.last().presentationTimeUs
            val cutUs = endUs - durationUs

            var keyIndex = -1
            frames.forEachIndexed { index, frame ->
                if (frame.kind == EncodedFrame.Kind.VIDEO &&
                    frame.isKeyframe &&
                    !frame.isConfig &&
                    frame.presentationTimeUs <= cutUs
                ) {
                    keyIndex = index
                }
            }

            val startIndex = if (keyIndex >= 0) {
                keyIndex
            } else {
                frames.indexOfFirst {
                    it.kind == EncodedFrame.Kind.VIDEO && it.isKeyframe && !it.isConfig
                }.let { if (it >= 0) it else 0 }
            }

            return frames.drop(startIndex)
        }
    }

    fun clear() {
        synchronized(lock) {
            frames.clear()
            bytes = 0
        }
    }

    fun stats(): Stats {
        synchronized(lock) {
            val start = frames.firstOrNull()?.presentationTimeUs ?: 0L
            val end = frames.lastOrNull()?.presentationTimeUs ?: 0L
            return Stats(
                frameCount = frames.size,
                byteCount = bytes,
                durationUs = (end - start).coerceAtLeast(0L),
            )
        }
    }

    private fun evictLocked() {
        while (frames.size >= 2 && spanLocked() > capacityUs) {
            val removed = frames.removeFirst()
            bytes -= removed.data.size
        }
        while (frames.isNotEmpty()) {
            val head = frames.first()
            val keepHead = head.isConfig ||
                (head.kind == EncodedFrame.Kind.VIDEO && head.isKeyframe) ||
                frames.size == 1
            if (keepHead) break
            val removed = frames.removeFirst()
            bytes -= removed.data.size
        }
        if (bytes < 0) bytes = 0
    }

    private fun spanLocked(): Long {
        if (frames.size < 2) return 0L
        return frames.last().presentationTimeUs - frames.first().presentationTimeUs
    }

    data class Stats(
        val frameCount: Int,
        val byteCount: Long,
        val durationUs: Long,
    )

    companion object {
        const val DEFAULT_CAPACITY_US = 40_000_000L
        const val DEFAULT_CLIP_US = 30_000_000L
    }
}
