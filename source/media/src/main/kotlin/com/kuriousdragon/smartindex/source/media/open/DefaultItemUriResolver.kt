package com.kuriousdragon.smartindex.source.media.open

import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ItemUriResolver
import com.kuriousdragon.smartindex.core.model.MediaContract
import com.kuriousdragon.smartindex.core.model.OpenTarget
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.TypeGating
import com.kuriousdragon.smartindex.source.media.di.MediaIo
import com.kuriousdragon.smartindex.source.media.map.MediaNames
import com.kuriousdragon.smartindex.source.media.store.MediaQueries
import com.kuriousdragon.smartindex.source.media.store.MediaStoreReader
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Resolves an indexed file item against its current MediaStore row (LLD — Media & file indexer §3.3.6, §5.5).
 * Reads only MediaStore metadata and whether the file exists, never its contents.
 */
class DefaultItemUriResolver @Inject constructor(
    private val reader: MediaStoreReader,
    private val permissions: PermissionService,
    private val files: FilePathLocator,
    private val fileUris: FileUriFactory,
    @MediaIo private val io: CoroutineDispatcher,
) : ItemUriResolver {
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override suspend fun resolve(type: ItemType, contentUri: String?): OpenTarget = withContext(io) {
        if (type !in MediaContract.FILE_TYPES) return@withContext OpenTarget.NotAFile
        val uri = MediaUris.parseMediaUri(contentUri) ?: return@withContext OpenTarget.NotAFile
        val group = TypeGating.groupOf(type)
        if (!TypeGating.isUsable(permissions.state.value[group])) return@withContext OpenTarget.NoAccess
        val row = try {
            reader.query(uri, MediaQueries.OPEN_PROJECTION, null, null, null, null)
                ?.use { c -> if (c.moveToFirst()) OpenRow.from(c) else null }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            return@withContext OpenTarget.NoAccess
        } catch (e: Exception) {
            SiLog.w(TAG, "open query failed class=${e.javaClass.simpleName}")
            null
        }
        if (row == null || row.isPending || row.isTrashed) return@withContext OpenTarget.Missing
        val name = MediaNames.title(row.displayName)
        val mime = MimeResolver.mimeFor(row.mimeType, name, type)
        when (type) {
            ItemType.DOCUMENT, ItemType.OTHER_FILE -> {
                val file = files.locate(row)?.takeIf { it.isFile } ?: return@withContext OpenTarget.Missing
                try {
                    OpenTarget.Ready(fileUris.uriFor(file), mime, name)
                } catch (_: IllegalArgumentException) { // path outside the configured roots
                    SiLog.w(TAG, "fileprovider root missing")
                    OpenTarget.Missing
                }
            }

            else -> OpenTarget.Ready(uri, mime, name) // PHOTO, VIDEO, RECORDING: MediaStore URI
        }
    }

    private companion object {
        const val TAG = "Media.Open"
    }
}
