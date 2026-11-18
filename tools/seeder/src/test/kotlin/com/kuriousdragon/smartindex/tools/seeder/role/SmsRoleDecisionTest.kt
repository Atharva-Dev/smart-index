package com.kuriousdragon.smartindex.tools.seeder.role

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmsRoleDecisionTest {
    private val own = "com.kuriousdragon.smartindex.seeder"

    @Test
    fun `the current default is saved when nothing is stored`() {
        assertThat(SmsRoleDecision.previousToStore(null, "com.google.android.apps.messaging", own))
            .isEqualTo("com.google.android.apps.messaging")
    }

    @Test
    fun `a stored previous package is never overwritten`() {
        assertThat(SmsRoleDecision.previousToStore("com.samsung.android.messaging", "com.other", own)).isNull()
    }

    @Test
    fun `our own package is never saved`() {
        assertThat(SmsRoleDecision.previousToStore(null, own, own)).isNull()
    }

    @Test
    fun `no current default saves nothing`() {
        assertThat(SmsRoleDecision.previousToStore(null, null, own)).isNull()
        assertThat(SmsRoleDecision.previousToStore("", "", own)).isNull()
    }

    @Test
    fun `an empty stored value counts as unset`() {
        assertThat(SmsRoleDecision.previousToStore("", "com.x", own)).isEqualTo("com.x")
    }

    @Test
    fun `restore command text`() {
        assertThat(SmsRoleDecision.restoreCommand("com.samsung.android.messaging")).isEqualTo(
            "adb shell cmd role add-role-holder --user 0 android.app.role.SMS com.samsung.android.messaging",
        )
        assertThat(SmsRoleDecision.acquireCommand(own)).endsWith("android.app.role.SMS $own")
    }
}
