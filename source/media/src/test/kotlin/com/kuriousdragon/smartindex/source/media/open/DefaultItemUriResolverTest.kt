package com.kuriousdragon.smartindex.source.media.open

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.webkit.MimeTypeMap
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.OpenTarget
import com.kuriousdragon.smartindex.source.media.store.MediaStoreReader
import com.kuriousdragon.smartindex.source.media.testing.FakeMediaStoreReader
import com.kuriousdragon.smartindex.source.media.testing.FakePermissionService
import com.kuriousdragon.smartindex.source.media.testing.row
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val PKG = "com.kuriousdragon.smartindex"
private const val PHOTO_URI = "content://media/external_primary/images/media/812"
private const val DOC_URI = "content://media/external_primary/file/4410"

/** Reader that serves rows with a DATA column (the shared fake kit has none) for the file-backed cases. */
private class DataReader : MediaStoreReader {
    var data: String? = null
    var name: String? = null
    var mime: String? = null
    var volume: String? = "external_primary"
    var relativePath: String? = null
    var pending = 0
    var trashed = 0
    var noRow = false

    override fun externalVolumes(): Set<String> = setOf("external_primary")

    override fun version(volume: String): String = "v1"

    override fun query(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        args: Array<String>?,
        sortOrder: String?,
        signal: CancellationSignal?,
    ): Cursor? {
        val c = MatrixCursor(projection)
        if (!noRow) {
            c.addRow(arrayOf<Any?>(4410L, name, mime, relativePath, volume, data, pending, trashed))
        }
        return c
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultItemUriResolverTest {
    private lateinit var root: File
    private lateinit var primary: File
    private lateinit var sd: File
    private val permissions = FakePermissionService(
        mapOf(Group.PHOTOS_VIDEOS to GroupState.GRANTED, Group.FILES to GroupState.GRANTED),
    )
    private val dataReader = DataReader()

    /** Maps `<root>/emulated/0/…` → `shared/…` and `<root>/<volume>/…` → `volumes/<volume>/…`; else throws. */
    private val factory = FileUriFactory { file ->
        val path = file.path
        val prefix = "$root/"
        require(path.startsWith(prefix)) { "outside roots" }
        val rel = path.removePrefix(prefix)
        val b = Uri.Builder().scheme("content").authority("$PKG.files")
        when {
            rel.startsWith("emulated/0/") -> b.appendPath("shared").also {
                rel.removePrefix("emulated/0/").split('/').forEach(b::appendPath)
            }

            rel.startsWith("1A2B-3C4D/") -> b.appendPath("volumes").also { rel.split('/').forEach(b::appendPath) }

            else -> throw IllegalArgumentException("outside roots")
        }
        b.build()
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("sidx59").toFile()
        primary = File(root, "emulated/0").also { it.mkdirs() }
        sd = File(root, "1A2B-3C4D").also { it.mkdirs() }
        shadowOf(MimeTypeMap.getSingleton()).apply {
            addExtensionMimeTypeMapping("pdf", "application/pdf")
            addExtensionMimeTypeMapping("zip", "application/zip")
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun resolver(reader: MediaStoreReader = dataReader) = DefaultItemUriResolver(
        reader,
        permissions,
        FilePathLocator { volume -> if (volume == "external_primary") primary else File(root, volume) },
        factory,
        Dispatchers.IO,
    )

    private fun file(dir: File, rel: String): File = File(dir, rel).also {
        it.parentFile!!.mkdirs()
        it.writeText("x")
    }

    private fun resolve(r: DefaultItemUriResolver, type: ItemType, uri: String?) =
        runBlocking { r.resolve(type, uri) }

    @Test
    fun `photo is ready with its MediaStore uri`() {
        val reader = FakeMediaStoreReader(
            listOf(row(id = 812, name = "IMG_20261103_091512.jpg", mime = "image/jpeg")),
        )
        assertThat(resolve(resolver(reader), ItemType.PHOTO, PHOTO_URI)).isEqualTo(
            OpenTarget.Ready(Uri.parse(PHOTO_URI), "image/jpeg", "IMG_20261103_091512.jpg"),
        )
        assertThat(reader.queries.single().uri.toString()).isEqualTo(PHOTO_URI)
        assertThat(reader.closedCursors).isEqualTo(1)
    }

    @Test
    fun `video and recording use the MediaStore uri`() {
        val video = "content://media/external_primary/video/media/5"
        val rec = "content://media/external_primary/audio/media/6"
        val vr = FakeMediaStoreReader(listOf(row(name = "clip.mp4", mime = "video/mp4")))
        assertThat(resolve(resolver(vr), ItemType.VIDEO, video))
            .isEqualTo(OpenTarget.Ready(Uri.parse(video), "video/mp4", "clip.mp4"))
        val rr = FakeMediaStoreReader(listOf(row(name = "Call.m4a", mime = "audio/mp4")))
        assertThat(resolve(resolver(rr), ItemType.RECORDING, rec))
            .isEqualTo(OpenTarget.Ready(Uri.parse(rec), "audio/mp4", "Call.m4a"))
    }

    @Test
    fun `document is ready through the file provider`() {
        val f = file(primary, "Download/Hotel booking.pdf")
        dataReader.data = f.path
        dataReader.name = "Hotel booking.pdf"
        dataReader.mime = "application/pdf"
        val t = resolve(resolver(), ItemType.DOCUMENT, DOC_URI) as OpenTarget.Ready
        assertThat(t.uri.toString()).isEqualTo("content://$PKG.files/shared/Download/Hotel%20booking.pdf")
        assertThat(t.mimeType).isEqualTo("application/pdf")
        assertThat(t.displayName).isEqualTo("Hotel booking.pdf")
    }

    @Test
    fun `other file on an SD card uses the volumes root`() {
        val f = file(sd, "Backups/a.zip")
        dataReader.data = f.path
        dataReader.name = "a.zip"
        dataReader.mime = null
        val t = resolve(resolver(), ItemType.OTHER_FILE, DOC_URI) as OpenTarget.Ready
        assertThat(t.uri.toString()).isEqualTo("content://$PKG.files/volumes/1A2B-3C4D/Backups/a.zip")
        assertThat(t.mimeType).isEqualTo("application/zip")
        assertThat(t.displayName).isEqualTo("a.zip")
    }

    @Test
    fun `file located through volume and relative path when DATA is blank`() {
        file(primary, "Documents/notes.pdf")
        dataReader.data = null
        dataReader.name = "notes.pdf"
        dataReader.relativePath = "Documents/"
        dataReader.mime = "application/pdf"
        val t = resolve(resolver(), ItemType.DOCUMENT, DOC_URI) as OpenTarget.Ready
        assertThat(t.uri.toString()).isEqualTo("content://$PKG.files/shared/Documents/notes.pdf")
    }

    @Test
    fun `renamed file reports its new display name`() {
        val f = file(primary, "Download/new name.pdf")
        dataReader.data = f.path
        dataReader.name = "new name.pdf"
        dataReader.mime = "application/pdf"
        val t = resolve(resolver(), ItemType.DOCUMENT, DOC_URI) as OpenTarget.Ready
        assertThat(t.displayName).isEqualTo("new name.pdf")
    }

    @Test
    fun `octet-stream with a pdf extension resolves to application pdf`() {
        val f = file(primary, "Download/a.pdf")
        dataReader.data = f.path
        dataReader.name = "a.pdf"
        dataReader.mime = "application/octet-stream"
        val t = resolve(resolver(), ItemType.DOCUMENT, DOC_URI) as OpenTarget.Ready
        assertThat(t.mimeType).isEqualTo("application/pdf")
    }

    @Test
    fun `unknown extension resolves to octet-stream`() {
        val f = file(primary, "Download/blob.zzzunknown")
        dataReader.data = f.path
        dataReader.name = "blob.zzzunknown"
        dataReader.mime = null
        val t = resolve(resolver(), ItemType.OTHER_FILE, DOC_URI) as OpenTarget.Ready
        assertThat(t.mimeType).isEqualTo("application/octet-stream")
    }

    @Test
    fun `photo with null mime resolves to image wildcard`() {
        val reader = FakeMediaStoreReader(listOf(row(name = "IMG", mime = null)))
        val t = resolve(resolver(reader), ItemType.PHOTO, PHOTO_URI) as OpenTarget.Ready
        assertThat(t.mimeType).isEqualTo("image/*")
    }

    @Test
    fun `blank display name falls back to the unnamed title`() {
        val reader = FakeMediaStoreReader(listOf(row(name = " ", mime = "image/jpeg")))
        val t = resolve(resolver(reader), ItemType.PHOTO, PHOTO_URI) as OpenTarget.Ready
        assertThat(t.displayName).isEqualTo("Unnamed file")
    }

    @Test
    fun `no row, pending or trashed is Missing`() {
        dataReader.noRow = true
        assertThat(resolve(resolver(), ItemType.DOCUMENT, DOC_URI)).isEqualTo(OpenTarget.Missing)
        dataReader.noRow = false
        dataReader.data = file(primary, "Download/a.pdf").path
        dataReader.name = "a.pdf"
        dataReader.pending = 1
        assertThat(resolve(resolver(), ItemType.DOCUMENT, DOC_URI)).isEqualTo(OpenTarget.Missing)
        dataReader.pending = 0
        dataReader.trashed = 1
        assertThat(resolve(resolver(), ItemType.DOCUMENT, DOC_URI)).isEqualTo(OpenTarget.Missing)
    }

    @Test
    fun `photo no longer selected under partial access is Missing`() {
        val reader = FakeMediaStoreReader()
        assertThat(resolve(resolver(reader), ItemType.PHOTO, PHOTO_URI)).isEqualTo(OpenTarget.Missing)
        val nullCursor = FakeMediaStoreReader().apply { nullCursorOnQuery(1) }
        assertThat(resolve(resolver(nullCursor), ItemType.PHOTO, PHOTO_URI)).isEqualTo(OpenTarget.Missing)
    }

    @Test
    fun `row present but file gone, a directory, or no path is Missing`() {
        dataReader.name = "gone.pdf"
        dataReader.data = File(primary, "Download/gone.pdf").path
        assertThat(resolve(resolver(), ItemType.DOCUMENT, DOC_URI)).isEqualTo(OpenTarget.Missing)
        File(primary, "Download/dir.pdf").mkdirs()
        dataReader.data = File(primary, "Download/dir.pdf").path
        assertThat(resolve(resolver(), ItemType.DOCUMENT, DOC_URI)).isEqualTo(OpenTarget.Missing)
        dataReader.data = null
        dataReader.volume = null
        assertThat(resolve(resolver(), ItemType.DOCUMENT, DOC_URI)).isEqualTo(OpenTarget.Missing)
    }

    @Test
    fun `file outside the provider roots is Missing`() {
        val outside = Files.createTempFile("sidx59-outside", ".pdf").toFile()
        try {
            dataReader.data = outside.path
            dataReader.name = "outside.pdf"
            assertThat(resolve(resolver(), ItemType.DOCUMENT, DOC_URI)).isEqualTo(OpenTarget.Missing)
        } finally {
            outside.delete()
        }
    }

    @Test
    fun `NoAccess when the group is not usable`() {
        permissions.set(Group.FILES, GroupState.DENIED)
        val reader = FakeMediaStoreReader(listOf(row()))
        assertThat(resolve(resolver(reader), ItemType.DOCUMENT, DOC_URI)).isEqualTo(OpenTarget.NoAccess)
        assertThat(resolve(resolver(reader), ItemType.RECORDING, DOC_URI)).isEqualTo(OpenTarget.NoAccess)
        assertThat(reader.queries).isEmpty()
    }

    @Test
    fun `partial photo access is usable`() {
        permissions.set(Group.PHOTOS_VIDEOS, GroupState.PARTIAL)
        val reader = FakeMediaStoreReader(listOf(row(name = "a.jpg")))
        assertThat(resolve(resolver(reader), ItemType.PHOTO, PHOTO_URI)).isInstanceOf(OpenTarget.Ready::class.java)
    }

    @Test
    fun `SecurityException from the query is NoAccess`() {
        val reader = FakeMediaStoreReader(listOf(row())).apply { failOnQuery(1, SecurityException("x")) }
        assertThat(resolve(resolver(reader), ItemType.PHOTO, PHOTO_URI)).isEqualTo(OpenTarget.NoAccess)
    }

    @Test
    fun `other reader exceptions are Missing`() {
        val reader = FakeMediaStoreReader(listOf(row())).apply { failOnQuery(1, IllegalStateException("boom")) }
        assertThat(resolve(resolver(reader), ItemType.PHOTO, PHOTO_URI)).isEqualTo(OpenTarget.Missing)
    }

    @Test
    fun `cancellation propagates`() {
        val reader = FakeMediaStoreReader(listOf(row())).apply { failOnQuery(1, CancellationException("c")) }
        assertThrows(CancellationException::class.java) { resolve(resolver(reader), ItemType.PHOTO, PHOTO_URI) }
    }

    @Test
    fun `calls, messages and non-media uris are NotAFile`() {
        val reader = FakeMediaStoreReader(listOf(row()))
        val r = resolver(reader)
        assertThat(resolve(r, ItemType.CALL, "content://call_log/calls/1")).isEqualTo(OpenTarget.NotAFile)
        assertThat(resolve(r, ItemType.MESSAGE, null)).isEqualTo(OpenTarget.NotAFile)
        assertThat(resolve(r, ItemType.PHOTO, null)).isEqualTo(OpenTarget.NotAFile)
        assertThat(resolve(r, ItemType.PHOTO, "content://other.authority/images/1")).isEqualTo(OpenTarget.NotAFile)
        assertThat(resolve(r, ItemType.PHOTO, "file:///sdcard/a.jpg")).isEqualTo(OpenTarget.NotAFile)
        assertThat(reader.queries).isEmpty()
    }
}
