package com.kuriousdragon.smartindex.source.media.map

import android.provider.MediaStore
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey

/** Maps a classified [MediaRow] to an [ItemRecord] (LLD §4.3–4.5). Pure: [nowMs] is passed in. */
internal object MediaMapper {
    /** Bump when the mapping output changes for the same row; part of [ItemRecord.sourceVersion]. */
    const val MAPPING_VERSION: Int = 1

    /** Dates before 1971-01-01 UTC are treated as missing. */
    const val MIN_VALID_MS: Long = 31_536_000_000L

    /** Dates more than this after "now" are treated as missing (clock or camera errors). */
    const val FUTURE_TOLERANCE_MS: Long = 24L * 60 * 60 * 1000

    /** Below this an epoch value is taken as seconds, at or above it as milliseconds. */
    private const val SECONDS_LIMIT: Long = 100_000_000_000L

    /** [row] must have a positive id and [type] must come from [MediaClassifier.classify]. */
    fun map(row: MediaRow, type: ItemType, nowMs: Long): ItemRecord {
        val id = requireNotNull(row.id).also { require(it > 0) }
        val taken = valid(toMs(row.dateTaken, seconds = false), nowMs)
        val added = valid(toMs(row.dateAddedSec, seconds = true), nowMs)
        val modified = valid(toMs(row.dateModifiedSec, seconds = true), nowMs)
        val duration = row.durationMs?.takeIf { it > 0 }
        val dates = LinkedHashMap<DateKind, Long>(4)
        val primary: Long
        when (type) {
            ItemType.PHOTO, ItemType.VIDEO -> {
                taken?.let { dates[DateKind.TAKEN] = it }
                added?.let { dates[DateKind.ADDED] = it }
                modified?.let { dates[DateKind.MODIFIED] = it }
                primary = taken ?: added ?: modified ?: nowMs
            }

            ItemType.DOCUMENT, ItemType.OTHER_FILE -> {
                added?.let { dates[DateKind.ADDED] = it }
                modified?.let { dates[DateKind.MODIFIED] = it }
                primary = listOfNotNull(added, modified).maxOrNull() ?: nowMs
            }

            ItemType.RECORDING -> {
                added?.let { dates[DateKind.ADDED] = it }
                modified?.let { dates[DateKind.MODIFIED] = it }
                // No EVENT: the matcher (SIDX-11) parses names and applies modified - duration itself.
                primary = modified ?: added ?: nowMs
            }

            ItemType.CALL, ItemType.MESSAGE -> throw IllegalArgumentException("not a media type")
        }
        if (dates.isEmpty()) dates[DateKind.ADDED] = primary
        val volume = row.volumeName?.trim()?.takeIf { it.isNotEmpty() } ?: MediaStore.VOLUME_EXTERNAL
        val size = row.size?.takeIf { it >= 0 }
        return ItemRecord(
            key = SourceKey(Source.MEDIA_STORE, id),
            type = type,
            title = MediaNames.title(row.displayName),
            primaryTs = primary,
            dates = dates,
            folder = MediaNames.folder(row.relativePath, isRecording = type == ItemType.RECORDING),
            mime = MediaClassifier.normalisedMime(row.mimeType).ifEmpty { null },
            ext = MediaNames.extension(row.displayName),
            sizeBytes = size,
            durationMs = duration,
            contentUri = contentUri(volume, id, type, row.mediaType),
            body = null,
            sourceVersion = sourceVersion(row),
        )
    }

    /** Typed MediaStore URI for media (ADR-012), Files URI otherwise. */
    fun contentUri(volume: String, id: Long, type: ItemType, mediaType: Int?): String = when {
        type == ItemType.PHOTO && mediaType != MediaStore.Files.FileColumns.MEDIA_TYPE_NONE ->
            MediaStore.Images.Media.getContentUri(volume, id)

        type == ItemType.VIDEO && mediaType != MediaStore.Files.FileColumns.MEDIA_TYPE_NONE ->
            MediaStore.Video.Media.getContentUri(volume, id)

        type == ItemType.RECORDING && mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO ->
            MediaStore.Audio.Media.getContentUri(volume, id)

        else -> MediaStore.Files.getContentUri(volume, id)
    }.toString()

    /** (MAPPING_VERSION << 48) | (GENERATION_MODIFIED & 0xFFFF_FFFF_FFFF); FNV-1a fallback without a generation. */
    fun sourceVersion(row: MediaRow): Long {
        val gen = row.generationModified
        if (gen != null && gen >= 0) return (MAPPING_VERSION.toLong() shl 48) or (gen and 0xFFFF_FFFF_FFFFL)
        var h = -0x340d631b7bdddcdbL // FNV-1a 64 offset basis
        val s = "$MAPPING_VERSION\u0000${row.dateModifiedSec}\u0000${row.size}\u0000${row.displayName}\u0000" +
            "${row.relativePath}\u0000${row.mimeType}\u0000${row.durationMs}\u0000${row.dateTaken}"
        for (c in s) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return h
    }

    fun toMs(raw: Long?, seconds: Boolean): Long? {
        if (raw == null || raw <= 0) return null
        return if (seconds) {
            if (raw >=
                SECONDS_LIMIT
            ) {
                raw
            } else {
                raw * 1000
            }
        } else {
            if (raw < SECONDS_LIMIT) raw * 1000 else raw
        }
    }

    fun valid(ms: Long?, nowMs: Long): Long? = ms?.takeIf { it >= MIN_VALID_MS && it <= nowMs + FUTURE_TOLERANCE_MS }
}
