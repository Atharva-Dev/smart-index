package com.kuriousdragon.smartindex.source.media.map

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.ItemType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val IMAGE = 1
private const val AUDIO = 2
private const val VIDEO = 3

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaClassifierTest {
    private val classifier = MediaClassifier(OWN_PACKAGE)

    private fun typeOf(row: MediaRow): ItemType {
        val result = classifier.classify(row)
        assertThat(result).isInstanceOf(Classification.Typed::class.java)
        return (result as Classification.Typed).type
    }

    private fun excludedOf(row: MediaRow): Exclusion {
        val result = classifier.classify(row)
        assertThat(result).isInstanceOf(Classification.Excluded::class.java)
        return (result as Classification.Excluded).reason
    }

    // --- exclusions E1-E6 ---

    @Test
    fun `E1 a pending row is excluded as PENDING`() {
        assertThat(excludedOf(mediaRow(pending = 1))).isEqualTo(Exclusion.PENDING)
    }

    @Test
    fun `E2 a trashed row is excluded as TRASHED`() {
        assertThat(excludedOf(mediaRow(trashed = 1))).isEqualTo(Exclusion.TRASHED)
    }

    @Test
    fun `E3 a row without MIME type is excluded as DIRECTORY`() {
        assertThat(excludedOf(mediaRow(name = "Camera", path = "DCIM/", mime = null)))
            .isEqualTo(Exclusion.DIRECTORY)
    }

    @Test
    fun `E4 app private folders are excluded ignoring case`() {
        assertThat(excludedOf(mediaRow(path = "Android/data/com.foo/", mime = "image/jpeg", mediaType = IMAGE)))
            .isEqualTo(Exclusion.APP_PRIVATE)
        assertThat(excludedOf(mediaRow(path = "android/OBB/com.foo/")))
            .isEqualTo(Exclusion.APP_PRIVATE)
    }

    @Test
    fun `E4 Android media folder is indexed`() {
        val row = mediaRow(
            name = "x.jpg",
            path = "Android/media/com.whatsapp/WhatsApp/Media/",
            mime = "image/jpeg",
            mediaType = IMAGE,
        )
        assertThat(typeOf(row)).isEqualTo(ItemType.PHOTO)
    }

    @Test
    fun `E5 a hidden path segment is excluded as HIDDEN`() {
        assertThat(excludedOf(mediaRow(name = "x.jpg", path = "WhatsApp/.Statuses/", mime = "image/jpeg")))
            .isEqualTo(Exclusion.HIDDEN)
        assertThat(excludedOf(mediaRow(path = "Android/.Trash/"))).isEqualTo(Exclusion.HIDDEN)
        assertThat(excludedOf(mediaRow(path = ".thumbnails/"))).isEqualTo(Exclusion.HIDDEN)
    }

    @Test
    fun `E5 a display name starting with a dot is excluded as HIDDEN`() {
        assertThat(excludedOf(mediaRow(name = ".nomedia"))).isEqualTo(Exclusion.HIDDEN)
        assertThat(excludedOf(mediaRow(name = "  .DS_Store"))).isEqualTo(Exclusion.HIDDEN)
    }

    @Test
    fun `E6 a file owned by our package is excluded as OWN_FILE`() {
        assertThat(excludedOf(mediaRow(name = "x.txt", path = "Documents/", mime = "text/plain", owner = OWN_PACKAGE)))
            .isEqualTo(Exclusion.OWN_FILE)
    }

    @Test
    fun `a file owned by another package is not excluded`() {
        assertThat(typeOf(mediaRow(name = "x.txt", mime = "text/plain", owner = "com.other")))
            .isEqualTo(ItemType.DOCUMENT)
    }

    @Test
    fun `exclusions are checked in order pending before trashed before directory`() {
        assertThat(excludedOf(mediaRow(pending = 1, trashed = 1, mime = null))).isEqualTo(Exclusion.PENDING)
        assertThat(excludedOf(mediaRow(trashed = 1, mime = null))).isEqualTo(Exclusion.TRASHED)
    }

    // --- types T1-T5 ---

    @Test
    fun `T1 call folder audio is a RECORDING`() {
        val row =
            mediaRow(
                name = "Rahul_241103_094215.m4a",
                path = "Recordings/Call/",
                mime = "audio/mp4a-latm",
                mediaType = AUDIO,
            )
        assertThat(typeOf(row)).isEqualTo(ItemType.RECORDING)
    }

    @Test
    fun `T2 an image is a PHOTO`() {
        assertThat(typeOf(mediaRow(name = "IMG.jpg", path = "DCIM/Camera/", mime = "image/jpeg", mediaType = IMAGE)))
            .isEqualTo(ItemType.PHOTO)
    }

    @Test
    fun `T3 a video is a VIDEO`() {
        assertThat(typeOf(mediaRow(name = "V.mp4", path = "DCIM/Camera/", mime = "video/mp4", mediaType = VIDEO)))
            .isEqualTo(ItemType.VIDEO)
    }

    @Test
    fun `T4 a PDF is a DOCUMENT`() {
        assertThat(typeOf(mediaRow(name = "a.pdf", path = "Download/", mime = "application/pdf")))
            .isEqualTo(ItemType.DOCUMENT)
    }

    @Test
    fun `T5 an archive is an OTHER_FILE`() {
        assertThat(typeOf(mediaRow(name = "a.zip", path = "Download/", mime = "application/zip")))
            .isEqualTo(ItemType.OTHER_FILE)
    }

    @Test
    fun `T5 an image with MEDIA_TYPE 0 is an OTHER_FILE`() {
        assertThat(typeOf(mediaRow(name = "a.jpg", path = "Pictures/", mime = "image/jpeg", mediaType = 0)))
            .isEqualTo(ItemType.OTHER_FILE)
    }

    @Test
    fun `T5 unknown MIME is an OTHER_FILE`() {
        assertThat(typeOf(mediaRow(name = "noext", mime = "application/x-unknown"))).isEqualTo(ItemType.OTHER_FILE)
    }

    @Test
    fun `MEDIA_TYPE null with image MIME is a PHOTO`() {
        assertThat(typeOf(mediaRow(name = "a.jpg", mime = "image/jpeg", mediaType = null))).isEqualTo(ItemType.PHOTO)
    }

    @Test
    fun `MEDIA_TYPE null with video MIME is a VIDEO`() {
        assertThat(typeOf(mediaRow(name = "a.mp4", mime = "video/mp4", mediaType = null))).isEqualTo(ItemType.VIDEO)
    }

    @Test
    fun `MIME with parameters is classified by its base type`() {
        assertThat(typeOf(mediaRow(name = "a", mime = "text/plain; charset=utf-8"))).isEqualTo(ItemType.DOCUMENT)
    }

    @Test
    fun `MIME matching ignores case`() {
        assertThat(typeOf(mediaRow(name = "a", mime = "Application/PDF"))).isEqualTo(ItemType.DOCUMENT)
    }

    // --- LLD examples ---

    @Test
    fun `example lower case call folder is a RECORDING`() {
        assertThat(typeOf(mediaRow(name = "x.m4a", path = "recordings/call/", mime = "audio/mp4", mediaType = AUDIO)))
            .isEqualTo(ItemType.RECORDING)
    }

    @Test
    fun `example Call folder amr with phone number name is a RECORDING`() {
        val row =
            mediaRow(name = "+919876543210_201231_101500.amr", path = "Call/", mime = "audio/amr", mediaType = AUDIO)
        assertThat(typeOf(row)).isEqualTo(ItemType.RECORDING)
    }

    @Test
    fun `example 3gp video in Call is a VIDEO and not audio`() {
        assertThat(typeOf(mediaRow(name = "x.3gp", path = "Call/", mime = "video/3gpp", mediaType = VIDEO)))
            .isEqualTo(ItemType.VIDEO)
    }

    @Test
    fun `example audio amr in Call is a RECORDING by MIME even without MEDIA_TYPE`() {
        assertThat(typeOf(mediaRow(name = "x.amr", path = "Call/", mime = "audio/amr", mediaType = null)))
            .isEqualTo(ItemType.RECORDING)
    }

    @Test
    fun `example Voice Recorder memo is an OTHER_FILE`() {
        assertThat(
            typeOf(
                mediaRow(name = "memo.m4a", path = "Recordings/Voice Recorder/", mime = "audio/mp4", mediaType = AUDIO),
            ),
        )
            .isEqualTo(ItemType.OTHER_FILE)
    }

    @Test
    fun `example csv with octet-stream MIME is a DOCUMENT`() {
        assertThat(typeOf(mediaRow(name = "a.csv", path = "Download/", mime = "application/octet-stream")))
            .isEqualTo(ItemType.DOCUMENT)
    }

    @Test
    fun `example zip is an OTHER_FILE`() {
        assertThat(typeOf(mediaRow(name = "a.zip", path = "Download/", mime = "application/zip")))
            .isEqualTo(ItemType.OTHER_FILE)
    }

    @Test
    fun `example a JPEG named pdf with PDF MIME is a DOCUMENT and a PDF named jpg with image type is a PHOTO`() {
        assertThat(typeOf(mediaRow(name = "photo.pdf", mime = "application/pdf"))).isEqualTo(ItemType.DOCUMENT)
        assertThat(
            typeOf(mediaRow(name = "notes.jpg", mime = "image/jpeg", mediaType = IMAGE)),
        ).isEqualTo(ItemType.PHOTO)
    }

    // --- R-REC ---

    private fun typeInPath(path: String?, mime: String = "audio/mp4", mediaType: Int? = AUDIO) =
        typeOf(mediaRow(name = "x.m4a", path = path, mime = mime, mediaType = mediaType))

    @Test
    fun `R-REC path case variants are recordings`() {
        for (p in listOf("RECORDINGS/CALL/", "recordings/call/", "Recordings/call/", "CALL/", "call/")) {
            assertThat(typeInPath(p)).isEqualTo(ItemType.RECORDING)
        }
    }

    @Test
    fun `R-REC a missing trailing slash is accepted`() {
        assertThat(typeInPath("Recordings/Call")).isEqualTo(ItemType.RECORDING)
        assertThat(typeInPath("Call")).isEqualTo(ItemType.RECORDING)
    }

    @Test
    fun `R-REC a leading slash is accepted`() {
        assertThat(typeInPath("/Recordings/Call/")).isEqualTo(ItemType.RECORDING)
        assertThat(typeInPath("/Call/")).isEqualTo(ItemType.RECORDING)
    }

    @Test
    fun `R-REC backslashes are accepted`() {
        assertThat(typeInPath("Recordings\\Call\\")).isEqualTo(ItemType.RECORDING)
    }

    @Test
    fun `R-REC subfolders are recordings`() {
        assertThat(typeInPath("Recordings/Call/2024/")).isEqualTo(ItemType.RECORDING)
        assertThat(typeInPath("Call/2024/11/")).isEqualTo(ItemType.RECORDING)
    }

    @Test
    fun `R-REC an SD card volume is a recording`() {
        val row =
            mediaRow(
                name = "x.m4a",
                path = "Recordings/Call/",
                volume = "1a2b-3c4d",
                mime = "audio/mp4",
                mediaType = AUDIO,
            )
        assertThat(typeOf(row)).isEqualTo(ItemType.RECORDING)
    }

    @Test
    fun `R-REC Voice Recorder Music Call and Download Call recordings are not recordings`() {
        for (p in listOf(
            "Recordings/Voice Recorder/",
            "Music/Call/",
            "Download/Call recordings/",
            "Recordings/",
            "Callbacks/",
        )) {
            assertThat(typeInPath(p)).isEqualTo(ItemType.OTHER_FILE)
        }
    }

    @Test
    fun `R-REC audio by MEDIA_TYPE alone is a recording`() {
        assertThat(
            typeInPath("Call/", mime = "application/octet-stream", mediaType = AUDIO),
        ).isEqualTo(ItemType.RECORDING)
    }

    @Test
    fun `R-REC audio by MIME alone is a recording`() {
        assertThat(typeInPath("Call/", mime = "audio/amr", mediaType = 0)).isEqualTo(ItemType.RECORDING)
    }

    @Test
    fun `R-REC m4a with octet-stream and MEDIA_TYPE 0 in Call is an OTHER_FILE`() {
        assertThat(typeInPath("Call/", mime = "application/octet-stream", mediaType = 0)).isEqualTo(ItemType.OTHER_FILE)
    }

    @Test
    fun `R-REC video 3gpp in Call is a VIDEO`() {
        assertThat(typeInPath("Call/", mime = "video/3gpp", mediaType = VIDEO)).isEqualTo(ItemType.VIDEO)
    }

    @Test
    fun `R-REC a PDF in Call is a DOCUMENT`() {
        assertThat(
            typeOf(mediaRow(name = "a.pdf", path = "Call/", mime = "application/pdf")),
        ).isEqualTo(ItemType.DOCUMENT)
    }

    @Test
    fun `isRecordingPath handles null and empty paths`() {
        assertThat(MediaClassifier.isRecordingPath(null)).isFalse()
        assertThat(MediaClassifier.isRecordingPath("")).isFalse()
        assertThat(MediaClassifier.isRecordingPath("/")).isFalse()
    }

    @Test
    fun `normalisedMime drops parameters and lower-cases`() {
        assertThat(MediaClassifier.normalisedMime("Text/Plain; charset=UTF-8")).isEqualTo("text/plain")
        assertThat(MediaClassifier.normalisedMime(null)).isEmpty()
        assertThat(MediaClassifier.normalisedMime("  ")).isEmpty()
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentMimeClassifierTest(private val mime: String) {
    @Test
    fun `each DOCUMENT_MIMES entry classifies a file with an unknown extension as DOCUMENT`() {
        val result = MediaClassifier(OWN_PACKAGE).classify(mediaRow(name = "file.unknownext", mime = mime))
        assertThat(result).isEqualTo(Classification.Typed(ItemType.DOCUMENT))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun mimes(): List<Array<Any>> = FileTypes.DOCUMENT_MIMES.sorted().map { arrayOf(it) }
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentExtClassifierTest(private val ext: String) {
    @Test
    fun `each DOCUMENT_EXTS entry classifies a generic-MIME file as DOCUMENT`() {
        val result = MediaClassifier(OWN_PACKAGE)
            .classify(mediaRow(name = "report.$ext", mime = "application/octet-stream"))
        assertThat(result).isEqualTo(Classification.Typed(ItemType.DOCUMENT))
    }

    @Test
    fun `an upper-case extension also matches`() {
        val result = MediaClassifier(OWN_PACKAGE)
            .classify(mediaRow(name = "REPORT.${ext.uppercase()}", mime = "application/octet-stream"))
        assertThat(result).isEqualTo(Classification.Typed(ItemType.DOCUMENT))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun exts(): List<Array<Any>> = FileTypes.DOCUMENT_EXTS.sorted().map { arrayOf(it) }
    }
}
