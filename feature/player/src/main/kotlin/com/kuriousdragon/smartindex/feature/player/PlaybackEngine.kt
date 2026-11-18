package com.kuriousdragon.smartindex.feature.player

import android.net.Uri

/** The seam between the state machine and Media3, so the state machine is a plain JVM test. */
interface PlaybackEngine {
    interface Listener {
        /** First STATE_READY. */
        fun onReady(durationMs: Long)

        fun onIsPlayingChanged(isPlaying: Boolean)

        fun onEnded()

        /** Any playback exception. */
        fun onError()
    }

    fun setListener(l: Listener)

    /** Sets the media item, prepares, and plays when ready. */
    fun start(uri: Uri)

    fun resume()

    fun pause()

    fun seekTo(positionMs: Long)

    val positionMs: Long

    fun release()
}

fun interface PlaybackEngineFactory {
    fun create(): PlaybackEngine
}
