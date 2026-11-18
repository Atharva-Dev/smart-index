package com.kuriousdragon.smartindex.feature.player

import android.net.Uri
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ItemUriResolver
import com.kuriousdragon.smartindex.core.model.OpenTarget
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred

internal class FakePlaybackEngine : PlaybackEngine {
    var current: PlaybackEngine.Listener? = null
    val started = mutableListOf<Uri>()
    var resumes = 0
    var pauses = 0
    val seeks = mutableListOf<Long>()
    var released = false
    override var positionMs: Long = 0

    override fun setListener(l: PlaybackEngine.Listener) {
        current = l
    }

    override fun start(uri: Uri) {
        started += uri
    }

    override fun resume() {
        resumes++
    }

    override fun pause() {
        pauses++
    }

    override fun seekTo(positionMs: Long) {
        seeks += positionMs
        this.positionMs = positionMs
    }

    override fun release() {
        released = true
    }
}

internal class FakeItemUriResolver : ItemUriResolver {
    var result: OpenTarget = OpenTarget.Ready(mockk<Uri>(), "audio/mpeg", "a.mp3")
    var gate: CompletableDeferred<Unit>? = null
    val calls = mutableListOf<Pair<ItemType, String?>>()

    override suspend fun resolve(type: ItemType, contentUri: String?): OpenTarget {
        calls += type to contentUri
        gate?.await()
        return result
    }
}
