package com.kuriousdragon.smartindex.source.media.map

import android.provider.MediaStore
import com.kuriousdragon.smartindex.core.model.ItemType

/** Why a row is not an item. Excluded rows are not counted as skipped and are not in the reconcile set. */
internal enum class Exclusion { DIRECTORY, PENDING, TRASHED, APP_PRIVATE, HIDDEN, OWN_FILE }

/** Result of [MediaClassifier.classify]. */
internal sealed interface Classification {
    data class Typed(val type: ItemType) : Classification
    data class Excluded(val reason: Exclusion) : Classification
}

/** Pure, stateless classification of a [MediaRow] (LLD §4.2). [ownPackage] = this app's package name. */
internal class MediaClassifier(private val ownPackage: String) {

    fun classify(row: MediaRow): Classification {
        exclusion(row)?.let { return Classification.Excluded(it) }
        val mime = normalisedMime(row.mimeType)
        val ext = MediaNames.extension(row.displayName)
        val mediaType = row.mediaType
        val isAudio = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO || mime.startsWith("audio/")
        val type = when {
            isAudio && isRecordingPath(row.relativePath) -> ItemType.RECORDING
            mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> ItemType.PHOTO
            mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> ItemType.VIDEO
            mediaType == null && mime.startsWith("image/") -> ItemType.PHOTO
            mediaType == null && mime.startsWith("video/") -> ItemType.VIDEO
            mime in FileTypes.DOCUMENT_MIMES || (ext != null && ext in FileTypes.DOCUMENT_EXTS) -> ItemType.DOCUMENT
            else -> ItemType.OTHER_FILE
        }
        return Classification.Typed(type)
    }

    private fun exclusion(row: MediaRow): Exclusion? {
        val path = MediaNames.normalisedPath(row.relativePath)
        return when {
            row.isPending == 1 -> Exclusion.PENDING
            row.isTrashed == 1 -> Exclusion.TRASHED
            row.mimeType == null -> Exclusion.DIRECTORY
            FileTypes.APP_PRIVATE_PREFIXES.any { path.startsWith(it, ignoreCase = true) } -> Exclusion.APP_PRIVATE
            path.split('/').any { it.startsWith(".") } -> Exclusion.HIDDEN
            row.displayName?.trim()?.startsWith(".") == true -> Exclusion.HIDDEN
            row.ownerPackage != null && row.ownerPackage == ownPackage -> Exclusion.OWN_FILE
            else -> null
        }
    }

    companion object {
        /** Lower-case MIME without parameters; "" when null/blank. */
        fun normalisedMime(mime: String?): String =
            mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()

        /** The RECORDING folder rule (LLD §4.2 R-REC), also used by tests and documented for SIDX-11. */
        fun isRecordingPath(relativePath: String?): Boolean {
            val p = MediaNames.normalisedPath(relativePath)
            return p.isNotEmpty() && FileTypes.RECORDING_PATH_PREFIXES.any { p.startsWith(it, ignoreCase = true) }
        }
    }
}
