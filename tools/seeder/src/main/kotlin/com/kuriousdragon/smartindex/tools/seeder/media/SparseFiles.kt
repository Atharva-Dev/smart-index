package com.kuriousdragon.smartindex.tools.seeder.media

import com.kuriousdragon.smartindex.tools.seeder.SeedLog
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Extends a file to its logical size by writing one byte at the end (a sparse hole; LLD §4.7). After
 * [PROBE_FILES] sparse files the drop in free space is compared with the logical bytes; if more than half of them
 * really consumed space the filesystem is not sparse and the writer switches to capped mode (≤ [CAP_BYTES] real
 * bytes) with `warn sparse-unsupported`. `sparse = false` starts in capped mode.
 */
class SparseFiles(sparse: Boolean, private val freeBytes: () -> Long) {
    @Volatile
    var capped: Boolean = !sparse
        private set

    private var extended = 0
    private var logicalTotal = 0L
    private var freeAtStart = -1L

    /** The size [extend] will actually produce for [logicalSize]. */
    fun effectiveSize(logicalSize: Long): Long = if (capped) minOf(logicalSize, CAP_BYTES) else logicalSize

    @Synchronized
    fun extend(fd: FileDescriptor, logicalSize: Long) {
        val size = effectiveSize(logicalSize)
        if (size <= 0) return
        // Not closed: the channel shares [fd], which its owner closes.
        val channel = FileOutputStream(fd).channel
        if (channel.size() >= size) return
        channel.position(size - 1)
        channel.write(ByteBuffer.wrap(byteArrayOf(1)))
        if (!capped && size > CAP_BYTES) probe(size)
    }

    private fun probe(size: Long) {
        if (extended == 0) freeAtStart = freeBytes()
        extended++
        logicalTotal += size
        if (extended != PROBE_FILES) return
        val drop = freeAtStart - freeBytes()
        if (logicalTotal >= MIN_PROBE_BYTES && drop > logicalTotal / 2) {
            capped = true
            SeedLog.warn("sparse-unsupported")
        }
    }

    companion object {
        const val PROBE_FILES = 20
        const val CAP_BYTES = 64L * 1024
        private const val MIN_PROBE_BYTES = 1L shl 20
    }
}
