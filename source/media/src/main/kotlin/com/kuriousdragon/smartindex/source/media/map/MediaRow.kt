package com.kuriousdragon.smartindex.source.media.map

/** One MediaStore Files row as read, before any interpretation. Every field is nullable (rows are untrusted). */
internal data class MediaRow(
    val id: Long?,
    val displayName: String?,
    val relativePath: String?,
    val volumeName: String?,
    val mimeType: String?,
    val mediaType: Int?,
    val size: Long?,
    val durationMs: Long?,
    val dateTaken: Long?, // MediaColumns.DATE_TAKEN, ms
    val dateAddedSec: Long?, // MediaColumns.DATE_ADDED, s
    val dateModifiedSec: Long?, // MediaColumns.DATE_MODIFIED, s
    val generationModified: Long?,
    val isPending: Int?,
    val isTrashed: Int?,
    val ownerPackage: String?,
)
