package com.kuriousdragon.smartindex.source.media.map

internal const val OWN_PACKAGE = "com.kuriousdragon.smartindex"

internal fun mediaRow(
    id: Long? = 1L,
    name: String? = "file.bin",
    path: String? = "Download/",
    volume: String? = "external_primary",
    mime: String? = "application/octet-stream",
    mediaType: Int? = 0,
    size: Long? = 10L,
    durationMs: Long? = null,
    taken: Long? = null,
    addedSec: Long? = null,
    modifiedSec: Long? = null,
    generation: Long? = 5L,
    pending: Int? = 0,
    trashed: Int? = 0,
    owner: String? = null,
) = MediaRow(
    id, name, path, volume, mime, mediaType, size, durationMs, taken, addedSec, modifiedSec,
    generation, pending, trashed, owner,
)
