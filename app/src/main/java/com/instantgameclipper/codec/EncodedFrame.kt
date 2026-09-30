package com.instantgameclipper.codec

import android.media.MediaCodec

/**
 * One encoded access unit held entirely in RAM.
 * [data] must already be a copy; never keep a MediaCodec output buffer.
 */
data class EncodedFrame(
    val kind: Kind,
    val data: ByteArray,
    val presentationTimeUs: Long,
    val flags: Int,
) {
    enum class Kind { VIDEO, AUDIO }

    val isKeyframe: Boolean
        get() = flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0

    val isConfig: Boolean
        get() = flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedFrame) return false
        return kind == other.kind &&
            presentationTimeUs == other.presentationTimeUs &&
            flags == other.flags &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + presentationTimeUs.hashCode()
        result = 31 * result + flags
        return result
    }
}
