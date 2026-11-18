package com.kuriousdragon.smartindex.tools.seeder.media

import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Three 64×64, 1 fps H.264 clips of 5 / 30 / 90 s made once with MediaCodec + MediaMuxer and cached in [dir]
 * (LLD §4.7). Frame `i` has presentation time `i` seconds, so the muxed duration equals the frame count.
 */
class Mp4Templates(private val dir: File) {
    private val memory = arrayOfNulls<ByteArray>(SECONDS.size)

    @Synchronized
    fun get(template: Int): ByteArray {
        val index = template.mod(SECONDS.size)
        memory[index]?.let { return it }
        val file = File(dir, "clip-${SECONDS[index]}s.mp4")
        if (file.length() == 0L) {
            dir.mkdirs()
            val tmp = File(dir, file.name + ".tmp")
            encode(tmp, SECONDS[index])
            check(tmp.renameTo(file)) { "cannot publish template" }
        }
        return file.readBytes().also { memory[index] = it }
    }

    private fun encode(target: File, frames: Int) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, SIZE, SIZE).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, 1)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1
            var fed = 0
            var inputDone = false
            var outputDone = false
            var written = 0
            val info = MediaCodec.BufferInfo()
            val deadline = System.nanoTime() + TIMEOUT_NS
            while (!outputDone) {
                check(System.nanoTime() < deadline) { "mp4 template encode timed out" }
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        if (fed < frames) {
                            val image = checkNotNull(codec.getInputImage(inIndex)) { "no input image" }
                            fill(image, fed)
                            codec.queueInputBuffer(inIndex, 0, SIZE * SIZE * 3 / 2, fed * 1_000_000L, 0)
                            fed++
                        } else {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                fed * 1_000_000L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                    }

                    outIndex >= 0 -> {
                        val data = codec.getOutputBuffer(outIndex)
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (data != null && info.size > 0 && !isConfig && track >= 0) {
                            data.position(info.offset)
                            data.limit(info.offset + info.size)
                            info.presentationTimeUs = written * 1_000_000L
                            muxer.writeSampleData(track, data, info)
                            written++
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            check(written == frames) { "encoder produced $written of $frames frames" }
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { codec.stop() }
            codec.release()
        }
    }

    /** Solid frame whose luma moves a little per frame so every frame is a distinct picture. */
    private fun fill(image: Image, index: Int) {
        val y = (60 + index % 100).toByte()
        for ((i, plane) in image.planes.withIndex()) {
            val value = if (i == 0) y else 128.toByte()
            val width = if (i == 0) SIZE else SIZE / 2
            val height = if (i == 0) SIZE else SIZE / 2
            val buffer = plane.buffer
            for (row in 0 until height) {
                for (col in 0 until width) buffer.put(row * plane.rowStride + col * plane.pixelStride, value)
            }
        }
    }

    companion object {
        val SECONDS = intArrayOf(5, 30, 90)
        const val SIZE = 64
        private const val BIT_RATE = 20_000
        private const val TIMEOUT_US = 10_000L
        private const val TIMEOUT_NS = 30_000_000_000L
    }
}
