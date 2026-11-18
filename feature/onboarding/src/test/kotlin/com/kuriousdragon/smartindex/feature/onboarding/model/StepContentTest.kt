package com.kuriousdragon.smartindex.feature.onboarding.model

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.feature.onboarding.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StepContentTest {
    private val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @Test
    fun `every group has non zero string and array ids that resolve`() {
        for (group in ONBOARDING_GROUPS) {
            val content = stepContent(group)
            assertThat(content.title).isNotEqualTo(0)
            assertThat(content.body).isNotEqualTo(0)
            assertThat(content.reads).isNotEqualTo(0)
            assertThat(content.readsHeading).isNotEqualTo(0)
            assertThat(content.cta).isNotEqualTo(0)
            assertThat(resources.getString(content.title)).isNotEmpty()
            assertThat(resources.getStringArray(content.reads)).isNotEmpty()
            assertThat(resources.getString(groupName(group))).isEqualTo(resources.getString(content.title))
        }
    }

    @Test
    fun `FILES uses step2 cta and the others use step cta`() {
        assertThat(stepContent(Group.FILES).cta).isEqualTo(R.string.step2_cta)
        assertThat(resources.getString(R.string.step2_cta)).isEqualTo("Continue to settings")
        ONBOARDING_GROUPS.filter { it != Group.FILES }.forEach {
            assertThat(stepContent(it).cta).isEqualTo(R.string.step_cta)
        }
    }

    @Test
    fun `step 3 uses the never sends reads heading`() {
        assertThat(stepContent(Group.MESSAGES).readsHeading).isEqualTo(R.string.step3_reads_heading)
        ONBOARDING_GROUPS.filter { it != Group.MESSAGES }.forEach {
            assertThat(stepContent(it).readsHeading).isEqualTo(R.string.step_reads_heading)
        }
    }

    @Test
    fun `reads arrays have the specified sizes and escaped text resolves`() {
        assertThat(resources.getStringArray(R.array.step1_reads)).hasLength(2)
        assertThat(resources.getStringArray(R.array.step2_reads)).hasLength(3)
        assertThat(resources.getStringArray(R.array.step2_reads)[0]).contains("what's inside")
        assertThat(resources.getString(R.string.summary_title)).isEqualTo("You're ready")
        assertThat(resources.getString(R.string.step_label, 4)).isEqualTo("Step 4 of 5")
    }

    @Test
    fun `group names match the spec`() {
        assertThat(resources.getString(groupName(Group.CALL_LOG))).isEqualTo("Call history")
        assertThat(resources.getString(groupName(Group.PHOTOS_VIDEOS))).isEqualTo("Photos & videos")
    }
}
