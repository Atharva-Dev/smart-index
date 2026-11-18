package com.kuriousdragon.smartindex.core.model

import android.graphics.Bitmap
import android.net.Uri

// Media & file contracts that the UI uses without importing :source:media (HLD §6, §7, ADR-012;
// Design Spec §6.5, §6.6). Owner: "LLD — Media & file indexer" (SIDX-8).
// Implemented by :source:media: MediaStoreThumbnailLoader (ThumbnailLoader), ThumbnailFetcher and
// ThumbnailKeyer (Coil 3 components for ThumbnailRequest, registered by :app) and
// DefaultItemUriResolver (ItemUriResolver).

/** Constants shared by the media indexer, the app manifest and the UI. */
object MediaContract {
    /** Suffix of the FileProvider authority. The `:app` manifest declares `${applicationId}.files`. */
    const val FILE_PROVIDER_AUTHORITY_SUFFIX: String = ".files"

    /** The FileProvider authority for [packageName] (pass `Context.packageName`). */
    fun fileProviderAuthority(packageName: String): String = packageName + FILE_PROVIDER_AUTHORITY_SUFFIX

    /** Item types that can have a MediaStore thumbnail. Every other type always shows its type tile. */
    val THUMBNAIL_TYPES: Set<ItemType> = setOf(ItemType.PHOTO, ItemType.VIDEO)

    /** Item types that are files on shared storage and can be opened through [ItemUriResolver]. */
    val FILE_TYPES: Set<ItemType> =
        setOf(ItemType.PHOTO, ItemType.VIDEO, ItemType.DOCUMENT, ItemType.OTHER_FILE, ItemType.RECORDING)
}

/**
 * The Coil model for a row's leading visual (Design Spec §6.5, HLD §11 Q2).
 *
 * The UI passes it as `model` to Coil's `AsyncImage`, with the type tile as `placeholder` and `error`:
 * while loading or on failure the row shows its type tile. `:app` registers the fetcher and keyer for
 * this class (from `:source:media`) in its `ImageLoader`, so feature modules never import `:source:media`.
 *
 * @property contentUri the item's MediaStore content URI ([ResultItem.contentUri]).
 * @property type the item's type; only [MediaContract.THUMBNAIL_TYPES] load a bitmap.
 * @property sizePx requested edge in pixels; the bitmap fits inside `sizePx × sizePx`.
 */
data class ThumbnailRequest(
    val contentUri: String,
    val type: ItemType,
    val sizePx: Int = IndexConfig.THUMBNAIL_SIZE_PX,
) {
    init {
        require(sizePx > 0) { "sizePx must be > 0" }
    }

    /** Memory-cache key used by the Coil keyer: the same URI and size give the same bitmap. */
    val cacheKey: String get() = "sidx-thumb:$sizePx:$contentUri"

    companion object {
        /**
         * The request for [item], or `null` when its type has no thumbnail or it has no URI.
         * `null` means: show the type tile, don't call Coil.
         */
        fun of(item: ResultItem, sizePx: Int = IndexConfig.THUMBNAIL_SIZE_PX): ThumbnailRequest? {
            val uri = item.contentUri ?: return null
            if (item.type !in MediaContract.THUMBNAIL_TYPES) return null
            return ThumbnailRequest(contentUri = uri, type = item.type, sizePx = sizePx)
        }
    }
}

/**
 * Loads system thumbnails with `ContentResolver.loadThumbnail` (HLD §4 Thumbnails, FR-017).
 * Implemented by `:source:media` (`MediaStoreThumbnailLoader`).
 */
interface ThumbnailLoader {
    /**
     * Returns a bitmap that fits inside `sizePx × sizePx`, or `null` when there is none: a type outside
     * [MediaContract.THUMBNAIL_TYPES], a malformed URI, a missing or unreadable file, a revoked
     * permission, or a corrupt, zero-byte or wrong-extension file the system can't decode.
     * Never throws, except `CancellationException`. Main-safe (switches to an IO dispatcher).
     */
    suspend fun load(request: ThumbnailRequest): Bitmap?
}

/** Where an item opens (FR-013, ADR-012), as returned by [ItemUriResolver.resolve]. */
sealed interface OpenTarget {
    /**
     * The item can be opened. The UI builds
     * `Intent(ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(FLAG_GRANT_READ_URI_PERMISSION)`
     * for Open, wraps the same intent in `Intent.createChooser` for Open with…, and catches
     * `ActivityNotFoundException` for the "No app on this phone can open …" snackbar.
     *
     * @property uri a MediaStore content URI (photos, videos, recordings) or a FileProvider URI
     *   (documents and other files, authority [MediaContract.fileProviderAuthority]).
     * @property mimeType the file's MIME type, never blank (`application/octet-stream` when unknown).
     * @property displayName the file's current name (it may have been renamed since it was indexed).
     */
    data class Ready(val uri: Uri, val mimeType: String, val displayName: String) : OpenTarget

    /** The file was moved off shared storage or deleted: show "This file was moved or deleted." */
    data object Missing : OpenTarget

    /** The permission for this item's group is no longer granted: offer the group's permission flow. */
    data object NoAccess : OpenTarget

    /** The item isn't a file (call, message) or has no content URI: nothing to open. */
    data object NotAFile : OpenTarget
}

/**
 * Turns an indexed file item into something an intent can open (FR-013, ADR-012). Implemented by
 * `:source:media` (`DefaultItemUriResolver`). Reads only MediaStore metadata and the file's existence,
 * never its contents.
 */
interface ItemUriResolver {
    /**
     * Resolves the item with [type] and stored [contentUri] ([ResultItem.contentUri]) against the current
     * MediaStore row. Never throws, except `CancellationException`. Main-safe.
     */
    suspend fun resolve(type: ItemType, contentUri: String?): OpenTarget
}

/** Resolves a list row. */
suspend fun ItemUriResolver.resolve(item: ResultItem): OpenTarget = resolve(item.type, item.contentUri)

/** Resolves an item detail (sheets). */
suspend fun ItemUriResolver.resolve(item: ItemDetail): OpenTarget = resolve(item.type, item.contentUri)
