package com.kuriousdragon.smartindex.sync

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.sync.di.SyncScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * In-process [ContentObserver]s on the trigger URIs, active only while the app is in the foreground
 * (LLD — Indexing orchestration §5.8, §6.5). A burst of changes requests one observer sync
 * [IndexConfig.FOREGROUND_CHANGE_DEBOUNCE_MS] after the last change. Never logs URIs.
 */
@Singleton
class ContentChangeObservers @Inject constructor(
    @ApplicationContext context: Context,
    private val control: DefaultIndexControl,
    @SyncScope private val scope: CoroutineScope,
) {
    private val resolver: ContentResolver = context.contentResolver
    private val changes =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var observer: ContentObserver? = null
    private var collectJob: Job? = null

    @MainThread
    fun register(uris: List<Uri>) {
        unregister()
        if (uris.isEmpty()) return
        val newObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                changes.tryEmit(Unit)
            }
        }
        uris.forEach { resolver.registerContentObserver(it, true, newObserver) }
        observer = newObserver
        collectJob = scope.launch {
            changes.debounce(IndexConfig.FOREGROUND_CHANGE_DEBOUNCE_MS).collect { control.requestObserverSync() }
        }
    }

    @MainThread
    fun unregister() {
        observer?.let { resolver.unregisterContentObserver(it) }
        observer = null
        collectJob?.cancel()
        collectJob = null
    }
}
