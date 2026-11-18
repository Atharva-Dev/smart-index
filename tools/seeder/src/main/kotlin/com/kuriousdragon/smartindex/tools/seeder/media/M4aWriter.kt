package com.kuriousdragon.smartindex.tools.seeder.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * AAC-LC 8 kHz mono 16 kbps silence (LLD §4.7): 1 s of silent PCM is encoded once; its access units and codec
 * config are kept and `write` muxes `ceil(durationMs / 128)` of them (one AAC frame = 1024 samples = 128 ms).
 */
class M4aWriter {
    private class Cached(val csd: ByteArray, val frames: List<ByteArray>)

    private var cached: Cached? = null

    /** [fd] must be read-write and seekable (`openFileDescriptor(uri, "rw")`). */
    @Synchronized
    fun write(fd: FileDescriptor, durationMs: Long) {
        val c = cached ?: encodeOnce().also { cached = it }
        val count = ((durationMs + FRAME_MS - 1) / FRAME_MS).toInt().coerceAtLeast(1)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 1).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(c.csd))
        }
        val muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val track = muxer.addTrack(format)
            muxer.start()
            val info = MediaCodec.BufferInfo()
            for (i in 0 until count) {
                val frame = c.frames[i % c.frames.size]
                info.set(0, frame.size, i * FRAME_MS * 1000L, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                muxer.writeSampleData(track, ByteBuffer.wrap(frame), info)
            }
            muxer.stop()
        } finally {
            muxer.release()
        }
    }

    private fun encodeOnce(): Cached {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val totalBytes = RATE * 2 // 1 s of 16-bit mono
            var fed = 0
            var inputDone = false
            var outputDone = false
            var csd: ByteArray? = null
            val frames = ArrayList<ByteArray>()
            val info = MediaCodec.BufferInfo()
            val deadline = System.nanoTime() + TIMEOUT_NS
            while (!outputDone) {
                check(System.nanoTime() < deadline) { "m4a encode timed out" }
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = checkNotNull(codec.getInputBuffer(inIndex))
                        if (fed < totalBytes) {
                            val n = minOf(buffer.capacity(), totalBytes - fed)
                            buffer.clear()
                            buffer.put(ByteArray(n))
                            codec.queueInputBuffer(inIndex, 0, n, fed / 2 * 1_000_000L / RATE, 0)
                            fed += n
                        } else {
                            codec.queueInputBuffer(inIndex, 0, 0, 1_000_000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex >= 0) {
                    val data = codec.getOutputBuffer(outIndex)
                    if (data != null && info.size > 0) {
                        val bytes = ByteArray(info.size)
                        data.position(info.offset)
                        data.get(bytes)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) csd = bytes else frames += bytes
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && csd == null) {
                    csd =
                        codec.outputFormat.getByteBuffer("csd-0")?.let { b ->
                            ByteArray(b.remaining()).also { b.get(it) }
                        }
                }
            }
            check(frames.isNotEmpty()) { "no AAC frames" }
            return Cached(checkNotNull(csd) { "no AAC codec config" }, frames)
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    companion object {
        const val RATE = 8_000
        const val BIT_RATE = 16_000
        const val FRAME_MS = 128L
        private const val TIMEOUT_US = 10_000L
        private const val TIMEOUT_NS = 20_000_000_000L
    }
}
