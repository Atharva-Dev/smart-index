package com.kuriousdragon.smartindex.feature.home.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.feature.home.model.HomeClock
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomePrefsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 1_700_000_000_000L
    private val clock = HomeClock { now }
    private val logged = mutableListOf<String>()
    private val oldSink = SiLog.sink

    @Before
    fun setUp() {
        SiLog.sink = SiLog.Sink { _, _, message -> logged += message }
    }

    @After
    fun tearDown() {
        SiLog.sink = oldSink
    }

    @Test
    fun `absent snooze reads as 0`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "a.preferences_pb") }
        assertThat(HomePrefs(store, clock).permissionBannerSnoozedUntil.first()).isEqualTo(0L)
    }

    @Test
    fun `snooze stores now plus seven days and survives a new store`() = runTest {
        val file = File(tmp.root, "b.preferences_pb")
        val first = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        HomePrefs(first, clock).snoozePermissionBanner()
        assertThat(HomePrefs(first, clock).permissionBannerSnoozedUntil.first())
            .isEqualTo(now + 7.days.inWholeMilliseconds)
    }

    @Test
    fun `an IOException reads as 0 and logs the class name`() = runTest {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("disk /data/x") }

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw IOException()
        }
        assertThat(HomePrefs(broken, clock).permissionBannerSnoozedUntil.first()).isEqualTo(0L)
        assertThat(logged).containsExactly("prefs read failed class=IOException")
    }
}
