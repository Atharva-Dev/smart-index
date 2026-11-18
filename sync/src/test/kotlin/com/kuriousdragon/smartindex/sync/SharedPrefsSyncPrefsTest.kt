package com.kuriousdragon.smartindex.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPrefsSyncPrefsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: SharedPrefsSyncPrefs

    @Before
    fun setUp() {
        context.getSharedPreferences("sidx_sync", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = SharedPrefsSyncPrefs(context)
    }

    @Test
    fun `defaults are disabled, no reindex, no ledger and no match run`() {
        assertThat(prefs.enabled).isFalse()
        assertThat(prefs.reindexRequested).isFalse()
        assertThat(prefs.grantedGroups).isNull()
        assertThat(prefs.lastMatchRun).isNull()
    }

    @Test
    fun `enabled round-trips`() {
        prefs.enabled = true
        assertThat(SharedPrefsSyncPrefs(context).enabled).isTrue()
        prefs.enabled = false
        assertThat(prefs.enabled).isFalse()
    }

    @Test
    fun `reindex requested round-trips`() {
        prefs.reindexRequested = true
        assertThat(SharedPrefsSyncPrefs(context).reindexRequested).isTrue()
        prefs.reindexRequested = false
        assertThat(prefs.reindexRequested).isFalse()
    }

    @Test
    fun `granted groups round-trip`() {
        prefs.grantedGroups = setOf(Group.FILES, Group.CALL_LOG)
        assertThat(SharedPrefsSyncPrefs(context).grantedGroups).containsExactly(Group.FILES, Group.CALL_LOG)
    }

    @Test
    fun `an empty granted groups set is not null`() {
        prefs.grantedGroups = emptySet()
        assertThat(prefs.grantedGroups).isEmpty()
        assertThat(prefs.grantedGroups).isNotNull()
    }

    @Test
    fun `setting granted groups to null removes the key`() {
        prefs.grantedGroups = setOf(Group.FILES)
        prefs.grantedGroups = null
        assertThat(prefs.grantedGroups).isNull()
        assertThat(context.getSharedPreferences("sidx_sync", Context.MODE_PRIVATE).contains("granted_groups")).isFalse()
    }

    @Test
    fun `unknown group names are ignored`() {
        context.getSharedPreferences("sidx_sync", Context.MODE_PRIVATE).edit()
            .putStringSet("granted_groups", setOf("FILES", "NOT_A_GROUP"))
            .commit()
        assertThat(prefs.grantedGroups).containsExactly(Group.FILES)
    }

    @Test
    fun `last match run round-trips and null removes it`() {
        prefs.lastMatchRun = 1_700_000_000_000L
        assertThat(SharedPrefsSyncPrefs(context).lastMatchRun).isEqualTo(1_700_000_000_000L)
        prefs.lastMatchRun = 0L
        assertThat(prefs.lastMatchRun).isEqualTo(0L)
        prefs.lastMatchRun = null
        assertThat(prefs.lastMatchRun).isNull()
    }
}
