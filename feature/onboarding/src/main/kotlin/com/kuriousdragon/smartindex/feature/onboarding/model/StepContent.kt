package com.kuriousdragon.smartindex.feature.onboarding.model

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.feature.onboarding.R

/** String and array resource ids for one permission step. */
data class StepContent(
    @StringRes val title: Int,
    @StringRes val body: Int,
    @ArrayRes val reads: Int,
    @StringRes val readsHeading: Int,
    @StringRes val cta: Int,
)

fun stepContent(group: Group): StepContent =
    when (group) {
        Group.PHOTOS_VIDEOS ->
            StepContent(
                R.string.step1_title,
                R.string.step1_body,
                R.array.step1_reads,
                R.string.step_reads_heading,
                R.string.step_cta,
            )

        Group.FILES ->
            StepContent(
                R.string.step2_title,
                R.string.step2_body,
                R.array.step2_reads,
                R.string.step_reads_heading,
                R.string.step2_cta,
            )

        Group.MESSAGES ->
            StepContent(
                R.string.step3_title,
                R.string.step3_body,
                R.array.step3_reads,
                R.string.step3_reads_heading,
                R.string.step_cta,
            )

        Group.CALL_LOG ->
            StepContent(
                R.string.step4_title,
                R.string.step4_body,
                R.array.step4_reads,
                R.string.step_reads_heading,
                R.string.step_cta,
            )

        Group.CONTACTS ->
            StepContent(
                R.string.step5_title,
                R.string.step5_body,
                R.array.step5_reads,
                R.string.step_reads_heading,
                R.string.step_cta,
            )
    }

@StringRes
fun groupName(group: Group): Int =
    when (group) {
        Group.PHOTOS_VIDEOS -> R.string.group_photos
        Group.FILES -> R.string.group_files
        Group.MESSAGES -> R.string.group_messages
        Group.CALL_LOG -> R.string.group_calls
        Group.CONTACTS -> R.string.group_contacts
    }
