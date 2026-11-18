package com.kuriousdragon.smartindex.core.permissions

import android.Manifest.permission.READ_CALL_LOG
import android.Manifest.permission.READ_CONTACTS
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.Manifest.permission.READ_SMS
import android.app.Activity
import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.PermissionRequest
import com.kuriousdragon.smartindex.core.model.RuntimeResult
import com.kuriousdragon.smartindex.core.model.SiLog
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DefaultPermissionServiceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val system = FakeSystemPermissions()
    private val flags = PermissionFlags(app)
    private lateinit var service: DefaultPermissionService

    @Before
    fun setUp() {
        app.getSharedPreferences("sidx_permissions", 0).edit().clear().commit()
        service = DefaultPermissionService(app, system, flags)
    }

    @After
    fun tearDown() {
        SiLog.sink = SiLog.LOGCAT
    }

    private fun stateOf(group: Group) = service.state.value.getValue(group)

    private fun result(
        group: Group,
        grants: Map<String, Boolean>,
        before: Set<String> = emptySet(),
        after: Set<String> = emptySet(),
    ) = RuntimeResult(group, grants, before, after)

    @Test
    fun `state always has all five groups and starts denied`() {
        assertThat(service.state.value.keys).containsExactlyElementsIn(Group.entries)
        assertThat(service.state.value.values.toSet()).containsExactly(GroupState.DENIED)
    }

    @Test
    fun `single permission groups are granted when the permission is granted`() {
        system.granted += listOf(READ_SMS, READ_CALL_LOG, READ_CONTACTS)
        system.storageManager = true
        service.refresh()
        assertThat(stateOf(Group.MESSAGES)).isEqualTo(GroupState.GRANTED)
        assertThat(stateOf(Group.CALL_LOG)).isEqualTo(GroupState.GRANTED)
        assertThat(stateOf(Group.CONTACTS)).isEqualTo(GroupState.GRANTED)
        assertThat(stateOf(Group.FILES)).isEqualTo(GroupState.GRANTED)
    }

    @Test
    fun `photos granted when images and video are granted`() {
        system.granted += listOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
        service.refresh()
        assertThat(stateOf(Group.PHOTOS_VIDEOS)).isEqualTo(GroupState.GRANTED)
    }

    @Test
    fun `photos partial with only images`() {
        system.granted += READ_MEDIA_IMAGES
        service.refresh()
        assertThat(stateOf(Group.PHOTOS_VIDEOS)).isEqualTo(GroupState.PARTIAL)
    }

    @Test
    fun `photos partial with only video`() {
        system.granted += READ_MEDIA_VIDEO
        service.refresh()
        assertThat(stateOf(Group.PHOTOS_VIDEOS)).isEqualTo(GroupState.PARTIAL)
    }

    @Test
    fun `photos partial with only visual user selected at sdk 34`() {
        system.granted += READ_MEDIA_VISUAL_USER_SELECTED
        service.refresh()
        assertThat(stateOf(Group.PHOTOS_VIDEOS)).isEqualTo(GroupState.PARTIAL)
    }

    @Test
    fun `visual user selected is ignored at sdk 33`() {
        system.sdkInt = 33
        system.granted += READ_MEDIA_VISUAL_USER_SELECTED
        service.refresh()
        assertThat(stateOf(Group.PHOTOS_VIDEOS)).isEqualTo(GroupState.DENIED)
    }

    @Test
    fun `files is denied without all files access and never permanently denied`() {
        service.onRuntimeResult(result(Group.FILES, mapOf("x" to false), before = setOf("x")))
        assertThat(stateOf(Group.FILES)).isEqualTo(GroupState.DENIED)
        assertThat(service.request(Group.FILES)).isInstanceOf(PermissionRequest.Settings::class.java)
    }

    @Test
    fun `first denial with rationale after is denied`() {
        service.onRuntimeResult(result(Group.MESSAGES, mapOf(READ_SMS to false), after = setOf(READ_SMS)))
        assertThat(stateOf(Group.MESSAGES)).isEqualTo(GroupState.DENIED)
        assertThat(flags.deniedOnce(READ_SMS)).isTrue()
        assertThat(flags.permanent(READ_SMS)).isFalse()
    }

    @Test
    fun `second denial is permanent`() {
        service.onRuntimeResult(result(Group.MESSAGES, mapOf(READ_SMS to false), after = setOf(READ_SMS)))
        service.onRuntimeResult(result(Group.MESSAGES, mapOf(READ_SMS to false), before = setOf(READ_SMS)))
        assertThat(stateOf(Group.MESSAGES)).isEqualTo(GroupState.DENIED_PERMANENTLY)
    }

    @Test
    fun `second denial without rationale either side is permanent through the asked once flag`() {
        service.onRuntimeResult(result(Group.CONTACTS, mapOf(READ_CONTACTS to false), after = setOf(READ_CONTACTS)))
        service.onRuntimeResult(result(Group.CONTACTS, mapOf(READ_CONTACTS to false)))
        assertThat(stateOf(Group.CONTACTS)).isEqualTo(GroupState.DENIED_PERMANENTLY)
    }

    @Test
    fun `denial with rationale before and none after is permanent`() {
        service.onRuntimeResult(result(Group.CALL_LOG, mapOf(READ_CALL_LOG to false), before = setOf(READ_CALL_LOG)))
        assertThat(stateOf(Group.CALL_LOG)).isEqualTo(GroupState.DENIED_PERMANENTLY)
    }

    @Test
    fun `dismissed dialog changes nothing`() {
        service.onRuntimeResult(result(Group.MESSAGES, mapOf(READ_SMS to false)))
        assertThat(stateOf(Group.MESSAGES)).isEqualTo(GroupState.DENIED)
        assertThat(flags.deniedOnce(READ_SMS)).isFalse()
        assertThat(flags.permanent(READ_SMS)).isFalse()
    }

    @Test
    fun `select photos result sets no flag and is partial`() {
        system.granted += READ_MEDIA_VISUAL_USER_SELECTED
        service.onRuntimeResult(
            result(
                Group.PHOTOS_VIDEOS,
                mapOf(
                    READ_MEDIA_IMAGES to false,
                    READ_MEDIA_VIDEO to false,
                    READ_MEDIA_VISUAL_USER_SELECTED to true,
                ),
                before = setOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO),
            ),
        )
        assertThat(flags.permanent(READ_MEDIA_IMAGES)).isFalse()
        assertThat(flags.permanent(READ_MEDIA_VIDEO)).isFalse()
        assertThat(flags.deniedOnce(READ_MEDIA_IMAGES)).isFalse()
        assertThat(stateOf(Group.PHOTOS_VIDEOS)).isEqualTo(GroupState.PARTIAL)
    }

    @Test
    fun `photos permanent when images permanently denied`() {
        service.onRuntimeResult(
            result(
                Group.PHOTOS_VIDEOS,
                mapOf(READ_MEDIA_IMAGES to false, READ_MEDIA_VIDEO to false, READ_MEDIA_VISUAL_USER_SELECTED to false),
                before = setOf(READ_MEDIA_IMAGES),
            ),
        )
        assertThat(stateOf(Group.PHOTOS_VIDEOS)).isEqualTo(GroupState.DENIED_PERMANENTLY)
    }

    @Test
    fun `grant clears flags so a later revoke is denied not permanent`() {
        service.onRuntimeResult(result(Group.MESSAGES, mapOf(READ_SMS to false), before = setOf(READ_SMS)))
        assertThat(stateOf(Group.MESSAGES)).isEqualTo(GroupState.DENIED_PERMANENTLY)
        system.granted += READ_SMS
        service.refresh()
        assertThat(stateOf(Group.MESSAGES)).isEqualTo(GroupState.GRANTED)
        assertThat(flags.permanent(READ_SMS)).isFalse()
        system.granted -= READ_SMS
        service.refresh()
        assertThat(stateOf(Group.MESSAGES)).isEqualTo(GroupState.DENIED)
    }

    @Test
    fun `flags survive a new service instance`() {
        service.onRuntimeResult(result(Group.MESSAGES, mapOf(READ_SMS to false), before = setOf(READ_SMS)))
        val second = DefaultPermissionService(app, system, PermissionFlags(app))
        assertThat(second.state.value.getValue(Group.MESSAGES)).isEqualTo(GroupState.DENIED_PERMANENTLY)
    }

    @Test
    fun `request returns already granted when granted`() {
        system.granted += READ_SMS
        service.refresh()
        assertThat(service.request(Group.MESSAGES)).isEqualTo(PermissionRequest.AlreadyGranted(Group.MESSAGES))
    }

    @Test
    fun `request returns runtime with exact photo permissions at sdk 34`() {
        assertThat(service.request(Group.PHOTOS_VIDEOS)).isEqualTo(
            PermissionRequest.Runtime(
                Group.PHOTOS_VIDEOS,
                listOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED),
            ),
        )
    }

    @Test
    fun `request returns runtime with exact photo permissions at sdk 33`() {
        system.sdkInt = 33
        assertThat(service.request(Group.PHOTOS_VIDEOS)).isEqualTo(
            PermissionRequest.Runtime(Group.PHOTOS_VIDEOS, listOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)),
        )
    }

    @Test
    fun `request from partial asks again`() {
        system.granted += READ_MEDIA_VISUAL_USER_SELECTED
        service.refresh()
        assertThat(service.request(Group.PHOTOS_VIDEOS)).isInstanceOf(PermissionRequest.Runtime::class.java)
    }

    @Test
    fun `request returns runtime for the single permission groups`() {
        assertThat(
            service.request(Group.MESSAGES),
        ).isEqualTo(PermissionRequest.Runtime(Group.MESSAGES, listOf(READ_SMS)))
        assertThat(service.request(Group.CALL_LOG))
            .isEqualTo(PermissionRequest.Runtime(Group.CALL_LOG, listOf(READ_CALL_LOG)))
        assertThat(service.request(Group.CONTACTS))
            .isEqualTo(PermissionRequest.Runtime(Group.CONTACTS, listOf(READ_CONTACTS)))
    }

    @Test
    fun `request returns app details settings when permanently denied`() {
        service.onRuntimeResult(result(Group.MESSAGES, mapOf(READ_SMS to false), before = setOf(READ_SMS)))
        val r = service.request(Group.MESSAGES) as PermissionRequest.Settings
        assertThat(r.intent.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(r.intent.data.toString()).isEqualTo("package:com.kuriousdragon.smartindex")
    }

    @Test
    fun `request for files opens the app all files access page`() {
        val r = service.request(Group.FILES) as PermissionRequest.Settings
        assertThat(r.intent.action).isEqualTo(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        assertThat(r.intent.data.toString()).isEqualTo("package:com.kuriousdragon.smartindex")
    }

    @Test
    fun `files settings falls back when the app page cannot be resolved`() {
        system.resolvable = false
        val intent = service.settingsIntent(Group.FILES)
        assertThat(intent.action).isEqualTo(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        assertThat(intent.data).isNull()
    }

    @Test
    fun `settings intent for other groups is app details`() {
        val intent = service.settingsIntent(Group.CONTACTS)
        assertThat(intent.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }

    @Test
    fun `refresh emits only on change`() = runTest {
        service.state.test {
            assertThat(awaitItem().getValue(Group.MESSAGES)).isEqualTo(GroupState.DENIED)
            service.refresh()
            expectNoEvents()
            system.granted += READ_SMS
            service.refresh()
            assertThat(awaitItem().getValue(Group.MESSAGES)).isEqualTo(GroupState.GRANTED)
            service.refresh()
            expectNoEvents()
        }
    }

    @Test
    fun `activity resume triggers refresh`() {
        assertThat(stateOf(Group.MESSAGES)).isEqualTo(GroupState.DENIED)
        system.granted += READ_SMS
        Robolectric.buildActivity(Activity::class.java).create().start().resume()
        assertThat(stateOf(Group.MESSAGES)).isEqualTo(GroupState.GRANTED)
    }

    @Test
    fun `security exception makes the group denied without throwing and logs only the class`() {
        val logged = mutableListOf<String>()
        SiLog.sink = SiLog.Sink { _, _, message -> logged += message }
        system.failing = true
        service.refresh()
        assertThat(service.state.value.keys).containsExactlyElementsIn(Group.entries)
        assertThat(service.state.value.values.toSet()).containsExactly(GroupState.DENIED)
        assertThat(logged).contains("check failed group=MESSAGES class=SecurityException")
        assertThat(logged.none { it.contains("boom") }).isTrue()
    }
}
