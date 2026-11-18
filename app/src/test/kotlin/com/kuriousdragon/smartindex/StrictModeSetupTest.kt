package com.kuriousdragon.smartindex

import android.app.Application
import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class StrictModeSetupTest {
    @Test
    fun `install replaces the lax thread and vm policies`() {
        StrictModeSetup.install()

        assertThat(StrictMode.getThreadPolicy()).isNotEqualTo(StrictMode.ThreadPolicy.LAX)
        assertThat(StrictMode.getVmPolicy()).isNotEqualTo(StrictMode.VmPolicy.LAX)
    }
}
