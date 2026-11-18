package com.kuriousdragon.smartindex.source.media.open

import com.kuriousdragon.smartindex.source.media.map.MediaNames
import java.io.File
import javax.inject.Inject

/** The root directory of a MediaStore volume (`StorageManager.getStorageVolume(Files.getContentUri(v))?.directory`). */
fun interface VolumeDirectories {
    fun directory(volumeName: String): File?
}

/** Finds the file behind a MediaStore row (LLD — Media & file indexer §3.3.6). */
class FilePathLocator @Inject constructor(private val volumes: VolumeDirectories) {
    /** DATA when non-blank, else `<volume dir>/<RELATIVE_PATH><DISPLAY_NAME>`; null when neither is possible. */
    fun locate(row: OpenRow): File? {
        row.data?.takeIf { it.isNotBlank() }?.let { return File(it) }
        val volume = row.volumeName?.takeIf { it.isNotBlank() } ?: return null
        val name = row.displayName?.takeIf { it.isNotBlank() } ?: return null
        val root = volumes.directory(volume) ?: return null
        return File(root, MediaNames.normalisedPath(row.relativePath) + name)
    }
}
