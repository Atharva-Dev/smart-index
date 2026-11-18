package com.kuriousdragon.smartindex.feature.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExoPlaybackEngineTest {
    @Test
    fun buildsAndReleasesWithoutExceptions() {
        val engine = ExoPlaybackEngine(ApplicationProvider.getApplicationContext<Context>())
        engine.setListener(
            object : PlaybackEngine.Listener {
                override fun onReady(durationMs: Long) = Unit

                override fun onIsPlayingChanged(isPlaying: Boolean) = Unit

                override fun onEnded() = Unit

                override fun onError() = Unit
            },
        )
        engine.release()
    }
}
