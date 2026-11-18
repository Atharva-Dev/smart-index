package com.kuriousdragon.smartindex.feature.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kuriousdragon.smartindex.core.model.SiLog

/**
 * Media3 implementation of [PlaybackEngine]. Main thread only. Audio focus loss and becoming-noisy arrive as
 * `isPlaying = false`. No MediaSession and no notification (ADR-011).
 */
internal class ExoPlaybackEngine(context: Context) : PlaybackEngine {
    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private var listener: PlaybackEngine.Listener? = null
    private var readyReported = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> if (!readyReported) {
                    readyReported = true
                    listener?.onReady(player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0)
                }

                Player.STATE_ENDED -> listener?.onEnded()

                else -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listener?.onIsPlayingChanged(isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            SiLog.w(TAG, "engine error", error)
            listener?.onError()
        }
    }

    init {
        player.addListener(playerListener)
    }

    override fun setListener(l: PlaybackEngine.Listener) {
        listener = l
    }

    override fun start(uri: Uri) {
        readyReported = false
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
    }

    override fun resume() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override val positionMs: Long get() = player.currentPosition

    override fun release() {
        listener = null
        player.removeListener(playerListener)
        player.release()
    }

    private companion object {
        const val TAG = "Player"
    }
}
