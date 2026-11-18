package com.kuriousdragon.smartindex.source.media.map

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Source
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaMapperTest {
    private val now = 1_730_000_000_000L // 2024-10-27
    private val hour = 3_600_000L

    private fun map(row: MediaRow, type: ItemType) = MediaMapper.map(row, type, now)

    private val takenMs = now - 5 * 86_400_000L
    private val addedSec = (now - 3 * 86_400_000L) / 1000
    private val modifiedSec = (now - 2 * 86_400_000L) / 1000

    // --- dates per type ---

    @Test
    fun `photo has TAKEN ADDED MODIFIED and primary is TAKEN`() {
        val r = map(mediaRow(taken = takenMs, addedSec = addedSec, modifiedSec = modifiedSec), ItemType.PHOTO)
        assertThat(r.dates).containsExactly(
            DateKind.TAKEN,
            takenMs,
            DateKind.ADDED,
            addedSec * 1000,
            DateKind.MODIFIED,
            modifiedSec * 1000,
        )
        assertThat(r.primaryTs).isEqualTo(takenMs)
    }

    @Test
    fun `video primary falls back to ADDED then MODIFIED`() {
        val a = map(mediaRow(addedSec = addedSec, modifiedSec = modifiedSec), ItemType.VIDEO)
        assertThat(a.primaryTs).isEqualTo(addedSec * 1000)
        assertThat(a.dates.keys).containsExactly(DateKind.ADDED, DateKind.MODIFIED)
        val m = map(mediaRow(modifiedSec = modifiedSec), ItemType.VIDEO)
        assertThat(m.primaryTs).isEqualTo(modifiedSec * 1000)
    }

    @Test
    fun `document has ADDED and MODIFIED and primary is the maximum`() {
        val r = map(mediaRow(addedSec = addedSec, modifiedSec = modifiedSec), ItemType.DOCUMENT)
        assertThat(r.dates.keys).containsExactly(DateKind.ADDED, DateKind.MODIFIED)
        assertThat(r.primaryTs).isEqualTo(modifiedSec * 1000)
        val r2 = map(mediaRow(addedSec = modifiedSec, modifiedSec = addedSec), ItemType.OTHER_FILE)
        assertThat(r2.primaryTs).isEqualTo(modifiedSec * 1000)
    }

    @Test
    fun `document never carries TAKEN even when DATE_TAKEN is set`() {
        val r = map(mediaRow(taken = takenMs, addedSec = addedSec), ItemType.DOCUMENT)
        assertThat(r.dates.keys).doesNotContain(DateKind.TAKEN)
    }

    @Test
    fun `recording primary is MODIFIED and duration is ignored`() {
        val r = map(mediaRow(durationMs = 60_000, addedSec = addedSec, modifiedSec = modifiedSec), ItemType.RECORDING)
        assertThat(r.primaryTs).isEqualTo(modifiedSec * 1000)
        assertThat(r.dates.keys).containsExactly(DateKind.ADDED, DateKind.MODIFIED)
    }

    @Test
    fun `recording primary falls back to ADDED when MODIFIED is invalid`() {
        val r = map(mediaRow(addedSec = addedSec, modifiedSec = 0), ItemType.RECORDING)
        assertThat(r.primaryTs).isEqualTo(addedSec * 1000)
        assertThat(r.dates.keys).containsExactly(DateKind.ADDED)
    }

    @Test
    fun `a recording never has an EVENT date`() {
        val r = map(mediaRow(taken = takenMs, addedSec = addedSec, modifiedSec = modifiedSec), ItemType.RECORDING)
        assertThat(r.dates.keys).doesNotContain(DateKind.EVENT)
        assertThat(r.dates.keys).doesNotContain(DateKind.TAKEN)
    }

    // --- heuristics and validity ---

    @Test
    fun `seconds in a milliseconds column are scaled up`() {
        val r = map(mediaRow(taken = takenMs / 1000), ItemType.PHOTO)
        assertThat(r.dates[DateKind.TAKEN]).isEqualTo(takenMs / 1000 * 1000)
    }

    @Test
    fun `milliseconds in a seconds column are kept`() {
        val r = map(mediaRow(addedSec = now - 86_400_000L), ItemType.DOCUMENT)
        assertThat(r.dates[DateKind.ADDED]).isEqualTo(now - 86_400_000L)
    }

    @Test
    fun `toMs treats zero negative and null as missing`() {
        assertThat(MediaMapper.toMs(null, seconds = true)).isNull()
        assertThat(MediaMapper.toMs(0, seconds = true)).isNull()
        assertThat(MediaMapper.toMs(-5, seconds = false)).isNull()
    }

    @Test
    fun `dates before 1971 are dropped`() {
        val r = map(mediaRow(addedSec = 100_000L, modifiedSec = modifiedSec), ItemType.DOCUMENT)
        assertThat(r.dates.keys).containsExactly(DateKind.MODIFIED)
        assertThat(MediaMapper.valid(MediaMapper.MIN_VALID_MS - 1, now)).isNull()
        assertThat(MediaMapper.valid(MediaMapper.MIN_VALID_MS, now)).isEqualTo(MediaMapper.MIN_VALID_MS)
    }

    @Test
    fun `now plus 23 hours is kept and now plus 25 hours is dropped`() {
        val keep = map(mediaRow(taken = now + 23 * hour), ItemType.PHOTO)
        assertThat(keep.dates[DateKind.TAKEN]).isEqualTo(now + 23 * hour)
        val drop = map(mediaRow(taken = now + 25 * hour), ItemType.PHOTO)
        assertThat(drop.dates).doesNotContainKey(DateKind.TAKEN)
    }

    @Test
    fun `no valid date gives ADDED now and primary now`() {
        for (type in listOf(
            ItemType.PHOTO,
            ItemType.VIDEO,
            ItemType.DOCUMENT,
            ItemType.OTHER_FILE,
            ItemType.RECORDING,
        )) {
            val r = map(mediaRow(taken = 0, addedSec = -1, modifiedSec = null), type)
            assertThat(r.dates).containsExactly(DateKind.ADDED, now)
            assertThat(r.primaryTs).isEqualTo(now)
        }
    }

    // --- size, duration, strings ---

    @Test
    fun `size minus one is null, zero is zero and 6 GB is kept`() {
        assertThat(map(mediaRow(size = -1), ItemType.OTHER_FILE).sizeBytes).isNull()
        assertThat(map(mediaRow(size = 0), ItemType.OTHER_FILE).sizeBytes).isEqualTo(0L)
        assertThat(map(mediaRow(size = 6L * 1024 * 1024 * 1024), ItemType.OTHER_FILE).sizeBytes)
            .isEqualTo(6L * 1024 * 1024 * 1024)
        assertThat(map(mediaRow(size = null), ItemType.OTHER_FILE).sizeBytes).isNull()
    }

    @Test
    fun `duration zero is null and positive is kept`() {
        assertThat(map(mediaRow(durationMs = 0), ItemType.VIDEO).durationMs).isNull()
        assertThat(map(mediaRow(durationMs = 1234), ItemType.VIDEO).durationMs).isEqualTo(1234L)
    }

    @Test
    fun `title folder mime and ext are derived`() {
        val r = map(
            mediaRow(name = " Report.PDF ", path = "Download/Work/", mime = "Application/PDF; x=y"),
            ItemType.DOCUMENT,
        )
        assertThat(r.title).isEqualTo("Report.PDF")
        assertThat(r.folder).isEqualTo("Work")
        assertThat(r.mime).isEqualTo("application/pdf")
        assertThat(r.ext).isEqualTo("pdf")
    }

    @Test
    fun `a recording folder is the whole path`() {
        val r =
            map(
                mediaRow(name = "Rahul_241103_094215.m4a", path = "Recordings/Call/", mediaType = 2),
                ItemType.RECORDING,
            )
        assertThat(r.folder).isEqualTo("Recordings/Call")
        assertThat(r.title).isEqualTo("Rahul_241103_094215.m4a")
    }

    @Test
    fun `blank mime maps to null and blank name to Unnamed file`() {
        val r = map(mediaRow(name = " ", mime = " "), ItemType.OTHER_FILE)
        assertThat(r.mime).isNull()
        assertThat(r.title).isEqualTo("Unnamed file")
    }

    @Test
    fun `key is MEDIA_STORE with the row id and body is always null`() {
        for (type in listOf(
            ItemType.PHOTO,
            ItemType.VIDEO,
            ItemType.DOCUMENT,
            ItemType.OTHER_FILE,
            ItemType.RECORDING,
        )) {
            val r = map(mediaRow(id = 42), type)
            assertThat(r.key.source).isEqualTo(Source.MEDIA_STORE)
            assertThat(r.key.sourceId).isEqualTo(42L)
            assertThat(r.body).isNull()
            assertThat(r.hasAttachment).isFalse()
            assertThat(r.numberRaw).isNull()
        }
    }

    @Test
    fun `a row without a positive id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { map(mediaRow(id = 0), ItemType.PHOTO) }
        assertThrows(IllegalArgumentException::class.java) { map(mediaRow(id = -3), ItemType.PHOTO) }
        assertThrows(IllegalArgumentException::class.java) { map(mediaRow(id = null), ItemType.PHOTO) }
    }

    @Test
    fun `CALL and MESSAGE are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { map(mediaRow(), ItemType.CALL) }
        assertThrows(IllegalArgumentException::class.java) { map(mediaRow(), ItemType.MESSAGE) }
    }

    // --- contentUri ---

    @Test
    fun `photo uses the Images URI`() {
        val r = map(mediaRow(id = 812, mediaType = 1), ItemType.PHOTO)
        assertThat(r.contentUri).isEqualTo("content://media/external_primary/images/media/812")
    }

    @Test
    fun `video uses the Video URI`() {
        val r = map(mediaRow(id = 7, mediaType = 3), ItemType.VIDEO)
        assertThat(r.contentUri).isEqualTo("content://media/external_primary/video/media/7")
    }

    @Test
    fun `recording with audio MEDIA_TYPE uses the Audio URI`() {
        val r = map(mediaRow(id = 9, mediaType = 2), ItemType.RECORDING)
        assertThat(r.contentUri).isEqualTo("content://media/external_primary/audio/media/9")
    }

    @Test
    fun `recording with a different MEDIA_TYPE uses the Files URI`() {
        val r = map(mediaRow(id = 9, mediaType = 0), ItemType.RECORDING)
        assertThat(r.contentUri).isEqualTo("content://media/external_primary/file/9")
        val n = map(mediaRow(id = 9, mediaType = null), ItemType.RECORDING)
        assertThat(n.contentUri).isEqualTo("content://media/external_primary/file/9")
    }

    @Test
    fun `document and other file use the Files URI`() {
        assertThat(
            map(mediaRow(id = 3), ItemType.DOCUMENT).contentUri,
        ).isEqualTo("content://media/external_primary/file/3")
        assertThat(
            map(mediaRow(id = 3), ItemType.OTHER_FILE).contentUri,
        ).isEqualTo("content://media/external_primary/file/3")
    }

    @Test
    fun `photo whose MEDIA_TYPE is NONE uses the Files URI`() {
        assertThat(map(mediaRow(id = 3, mediaType = 0), ItemType.PHOTO).contentUri)
            .isEqualTo("content://media/external_primary/file/3")
    }

    @Test
    fun `null or blank volume falls back to external`() {
        assertThat(map(mediaRow(id = 5, volume = null, mediaType = 1), ItemType.PHOTO).contentUri)
            .isEqualTo("content://media/external/images/media/5")
        assertThat(map(mediaRow(id = 5, volume = " ", mediaType = 0), ItemType.DOCUMENT).contentUri)
            .isEqualTo("content://media/external/file/5")
    }

    @Test
    fun `an SD card volume appears in the URI`() {
        assertThat(map(mediaRow(id = 5, volume = "1a2b-3c4d", mediaType = 1), ItemType.PHOTO).contentUri)
            .isEqualTo("content://media/1a2b-3c4d/images/media/5")
    }

    // --- sourceVersion ---

    @Test
    fun `sourceVersion combines mapping version and generation`() {
        assertThat(map(mediaRow(generation = 1203), ItemType.PHOTO).sourceVersion).isEqualTo((1L shl 48) or 1203L)
        assertThat(MediaMapper.sourceVersion(mediaRow(generation = 0))).isEqualTo(1L shl 48)
    }

    @Test
    fun `sourceVersion changes with the generation`() {
        assertThat(MediaMapper.sourceVersion(mediaRow(generation = 1)))
            .isNotEqualTo(MediaMapper.sourceVersion(mediaRow(generation = 2)))
    }

    @Test
    fun `sourceVersion masks the generation to 48 bits`() {
        val gen = (1L shl 50) or 7L
        assertThat(MediaMapper.sourceVersion(mediaRow(generation = gen))).isEqualTo((1L shl 48) or 7L)
    }

    @Test
    fun `fallback hash is stable without a generation`() {
        val a = mediaRow(generation = null, name = "a.jpg", modifiedSec = 10)
        assertThat(MediaMapper.sourceVersion(a)).isEqualTo(MediaMapper.sourceVersion(a.copy()))
    }

    @Test
    fun `fallback hash differs for different names and for a negative generation`() {
        val a = mediaRow(generation = null, name = "a.jpg")
        val b = mediaRow(generation = null, name = "b.jpg")
        assertThat(MediaMapper.sourceVersion(a)).isNotEqualTo(MediaMapper.sourceVersion(b))
        assertThat(MediaMapper.sourceVersion(mediaRow(generation = -1, name = "a.jpg")))
            .isEqualTo(MediaMapper.sourceVersion(a))
    }
}
