package com.kuriousdragon.smartindex.source.media.thumb

import android.graphics.Bitmap
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.util.Size
import com.kuriousdragon.smartindex.core.model.MediaContract
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.ThumbnailLoader
import com.kuriousdragon.smartindex.core.model.ThumbnailRequest
import com.kuriousdragon.smartindex.source.media.di.MediaIo
import com.kuriousdragon.smartindex.source.media.open.MediaUris
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [ThumbnailLoader] over the system thumbnail call (LLD — Media & file indexer §3.3.4, §5.6). */
class MediaStoreThumbnailLoader @Inject constructor(
    private val system: SystemThumbnails,
    @param:MediaIo private val io: CoroutineDispatcher,
) : ThumbnailLoader {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun load(request: ThumbnailRequest): Bitmap? = withContext(io) {
        if (request.type !in MediaContract.THUMBNAIL_TYPES) return@withContext null
        val uri = MediaUris.parseMediaUri(request.contentUri) ?: return@withContext null
        val signal = CancellationSignal()
        // Unconfined: the cancel must run on the cancelling thread, while the IO thread is blocked in the system call.
        val watcher = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                signal.cancel()
            }
        }
        try {
            system.load(uri, Size(request.sizePx, request.sizePx), signal)
        } catch (e: CancellationException) {
            throw e
        } catch (_: OperationCanceledException) {
            coroutineContext.ensureActive()
            null
        } catch (e: Exception) {
            SiLog.d(TAG, "thumb failed type=${request.type} class=${e.javaClass.simpleName}")
            null
        } finally {
            watcher.cancel()
        }
    }

    private companion object {
        const val TAG = "Media.Thumb"
    }
}
