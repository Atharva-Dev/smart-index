package com.kuriousdragon.smartindex.tools.seeder.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.kuriousdragon.smartindex.tools.seeder.SeedLog
import com.kuriousdragon.smartindex.tools.seeder.bytes.AmrWriter
import com.kuriousdragon.smartindex.tools.seeder.bytes.DocBytes
import com.kuriousdragon.smartindex.tools.seeder.bytes.ExifSplicer
import com.kuriousdragon.smartindex.tools.seeder.bytes.Mp4TimePatcher
import com.kuriousdragon.smartindex.tools.seeder.gen.MediaKind
import com.kuriousdragon.smartindex.tools.seeder.gen.Payload
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedFile
import com.kuriousdragon.smartindex.tools.seeder.wipe.Ledger
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Writes one planned file through MediaStore (LLD §4.7): insert with `IS_PENDING=1` into the collection for its
 * kind on `external_primary`, write the payload, sparse-extend, set the file mtime, publish. Every
 * [VERIFY_EVERY] rows the batch's `DATE_MODIFIED` is read back and mismatches (> 2 s) are rescanned with
 * [MediaScannerConnection] (counted in [rescanned]); `setLastModified == false` is counted in [mtimeFailed].
 * Every created URI is appended to [ledger] as kind `file`.
 */
class MediaStoreWriter(
    private val context: Context,
    private val ledger: Ledger,
    private val sparse: SparseFiles,
    private val zone: ZoneId,
    templatesDir: File = File(context.filesDir, "templates"),
) {
    private val resolver: ContentResolver = context.contentResolver
    private val jpegs = JpegTemplates(templatesDir)
    private val mp4s = Mp4Templates(templatesDir)
    private val m4a = M4aWriter()
    private val pending = ArrayList<Pair<Uri, SeedFile>>(VERIFY_EVERY)
    private val failureClasses = HashSet<String>()

    @Volatile
    var rescanned: Int = 0
        private set

    @Volatile
    var mtimeFailed: Int = 0
        private set

    /** Returns the published URI or null when the row failed (it is then removed again). */
    suspend fun write(file: SeedFile): Uri? = withContext(Dispatchers.IO) {
        var uri: Uri? = null
        try {
            uri = insertPending(file)
            writePayload(uri, file)
            val path = File(Environment.getExternalStorageDirectory(), file.relativePath + file.displayName)
            if (!path.setLastModified(file.modifiedMs)) mtimeFailed++
            val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            check(resolver.update(uri, publish, null, null) > 0) { "publish updated no row" }
            ledger.append("file", uri.toString())
            synchronized(pending) { pending += uri to file }
            if (pending.size >= VERIFY_EVERY) verifyPending()
            uri
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (failureClasses.add(e.javaClass.simpleName)) SeedLog.error("file ${e.javaClass.simpleName}")
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            null
        }
    }

    /** Verifies the rows written since the last check (call at the end of a phase). */
    suspend fun verifyPending() {
        val batch = synchronized(pending) { pending.toList().also { pending.clear() } }
        if (batch.isEmpty()) return
        val wrong = withContext(Dispatchers.IO) { mismatched(batch) }
        if (wrong.isEmpty()) return
        rescanned += wrong.size
        scan(wrong.map { File(Environment.getExternalStorageDirectory(), it.relativePath + it.displayName).path })
    }

    private fun mismatched(batch: List<Pair<Uri, SeedFile>>): List<SeedFile> {
        val plannedById = batch.associate { (uri, f) -> ContentUris.parseId(uri) to f }
        val actual = HashMap<Long, Long>()
        val filesUri = MediaStore.Files.getContentUri(VOLUME)
        plannedById.keys.chunked(VERIFY_EVERY).forEach { ids ->
            resolver.query(
                filesUri,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_MODIFIED),
                "${MediaStore.MediaColumns._ID} IN (${ids.joinToString(",")})",
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) actual[c.getLong(0)] = c.getLong(1)
            }
        }
        return plannedById.filter { (id, f) -> abs((actual[id] ?: Long.MIN_VALUE / 2) - f.modifiedMs / 1000) > 2 }
            .values.toList()
    }

    private suspend fun scan(paths: List<String>) {
        if (paths.isEmpty()) return
        suspendCancellableCoroutine { cont ->
            var remaining = paths.size
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                val done = synchronized(this) { --remaining == 0 }
                if (done && cont.isActive) cont.resume(Unit)
            }
        }
    }

    private fun insertPending(file: SeedFile): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.displayName)
            put(MediaStore.MediaColumns.RELATIVE_PATH, file.relativePath)
            put(MediaStore.MediaColumns.MIME_TYPE, file.mime)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (file.kind == MediaKind.PHOTO || file.kind == MediaKind.VIDEO) {
                file.takenMs?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
            }
        }
        val primary = collection(file)
        return try {
            resolver.insert(primary, values)
        } catch (e: IllegalArgumentException) {
            // e.g. the Audio collection refuses a legacy top-level `Call/` folder; the Files collection does not.
            resolver.insert(MediaStore.Files.getContentUri(VOLUME), values)
        } ?: error("insert returned null")
    }

    private fun collection(file: SeedFile): Uri = when {
        file.kind == MediaKind.PHOTO -> MediaStore.Images.Media.getContentUri(VOLUME)

        file.kind == MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(VOLUME)

        file.kind == MediaKind.RECORDING || file.mime.startsWith(
            "audio/",
        ) -> MediaStore.Audio.Media.getContentUri(VOLUME)

        else -> MediaStore.Files.getContentUri(VOLUME)
    }

    private fun writePayload(uri: Uri, file: SeedFile) {
        // "rw": MediaMuxer needs a seekable read-write descriptor.
        val pfd = resolver.openFileDescriptor(uri, "rw") ?: error("no descriptor")
        pfd.use {
            val fd = it.fileDescriptor
            when (val payload = file.payload) {
                is Payload.Jpeg -> {
                    val taken = Instant.ofEpochMilli(file.takenMs ?: file.modifiedMs).atZone(zone)
                    writeAll(fd, ExifSplicer.splice(jpegs.get(payload.template), taken.toLocalDateTime(), taken.offset))
                }

                is Payload.Mp4 -> {
                    val seconds = Math.floorDiv(file.takenMs ?: file.modifiedMs, 1000L)
                    writeAll(fd, Mp4TimePatcher.patch(mp4s.get(payload.template), seconds))
                }

                Payload.Amr -> writeAll(fd, AmrWriter.silence(file.durationMs ?: 0L))

                Payload.M4a -> m4a.write(fd, file.durationMs ?: 0L)

                is Payload.Doc -> {
                    if (file.logicalSize > 0) {
                        val head = DocBytes.header(payload.ext, payload.text)
                        writeAll(fd, head)
                        sparse.extend(fd, file.logicalSize)
                    }
                }
            }
        }
    }

    private fun writeAll(fd: java.io.FileDescriptor, bytes: ByteArray) {
        FileOutputStream(fd).write(bytes) // the descriptor is closed by the caller, not here
    }

    companion object {
        const val VERIFY_EVERY = 500
        private const val VOLUME = "external_primary"
    }
}
