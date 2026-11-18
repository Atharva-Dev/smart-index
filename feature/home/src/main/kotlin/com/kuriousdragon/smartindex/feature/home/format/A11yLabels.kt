package com.kuriousdragon.smartindex.feature.home.format

import android.content.res.Resources
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv
import com.kuriousdragon.smartindex.feature.home.model.Tab
import java.text.NumberFormat

/** Spoken labels and TalkBack custom actions (Design Spec section 8, LLD 4.10). */
object A11yLabels {
    private const val SPOKEN_SEP = ", "

    /** The label of a row's clickable area; parts that are missing are left out. */
    fun rowLabel(item: ResultItem, env: DeviceEnv, res: Resources): String {
        val time = TimeLabels.format(item.time, env)
        return when (item.type) {
            ItemType.PHOTO, ItemType.VIDEO, ItemType.DOCUMENT, ItemType.OTHER_FILE -> {
                val noun = when (item.type) {
                    ItemType.PHOTO -> R.string.a11y_type_photo
                    ItemType.VIDEO -> R.string.a11y_type_video
                    ItemType.DOCUMENT -> R.string.a11y_type_document
                    else -> R.string.a11y_type_file
                }
                spoken(
                    res.getString(noun),
                    item.title,
                    RowFormatter.folderSegment(item.folder)?.first,
                    item.sizeBytes?.let { SizeFormat.format(it, env.locale) },
                    time,
                )
            }

            ItemType.CALL -> spoken(
                res.getString(callNoun(item.direction)),
                item.contactName.nonBlank() ?: item.title,
                item.durationMs?.let { DurationFormat.call(it, res) },
                time,
                if (item.linkedRecordingId != null) res.getString(R.string.a11y_has_recording) else null,
            )

            ItemType.RECORDING -> spoken(
                res.getString(R.string.a11y_row_recording),
                item.contactName.nonBlank() ?: item.number.nonBlank() ?: item.title,
                item.durationMs?.let(DurationFormat::media),
                time,
            )

            ItemType.MESSAGE -> {
                val who = item.contactName.nonBlank() ?: item.title
                val text = item.snippet?.takeIf { it.isNotEmpty() } ?: res.getString(R.string.a11y_msg_no_text)
                val template = when (item.direction) {
                    Direction.RECEIVED -> R.string.a11y_row_msg_received
                    Direction.SENT -> R.string.a11y_row_msg_sent
                    else -> R.string.a11y_row_msg_generic
                }
                res.getString(template, who, time, text)
            }
        }
    }

    /** The custom actions of a row, in the order TalkBack lists them. */
    fun actions(item: ResultItem): List<RowAction> = when (item.type) {
        ItemType.PHOTO, ItemType.VIDEO, ItemType.DOCUMENT, ItemType.OTHER_FILE ->
            listOf(RowAction.OPEN_WITH, RowAction.MORE_ACTIONS)

        ItemType.CALL -> buildList {
            val hasNumber = item.number.nonBlank() != null
            if (hasNumber) add(RowAction.CALL_BACK)
            if (hasNumber) {
                add(
                    if (item.contactName.nonBlank() !=
                        null
                    ) {
                        RowAction.OPEN_CONTACT
                    } else {
                        RowAction.ADD_CONTACT
                    },
                )
            }
            if (item.linkedRecordingId != null) add(RowAction.PLAY_RECORDING)
        }

        ItemType.RECORDING -> listOf(RowAction.PLAY, RowAction.OPEN_WITH_AUDIO)

        ItemType.MESSAGE -> emptyList()
    }

    /** The visible name TalkBack announces for a custom action. */
    fun actionLabel(action: RowAction, res: Resources): String = res.getString(
        when (action) {
            RowAction.OPEN_WITH, RowAction.OPEN_WITH_AUDIO -> R.string.a11y_action_open_with
            RowAction.MORE_ACTIONS -> R.string.a11y_action_more
            RowAction.CALL_BACK -> R.string.sheet_call_back
            RowAction.OPEN_CONTACT -> R.string.sheet_open_contact
            RowAction.ADD_CONTACT -> R.string.sheet_add_contact
            RowAction.PLAY_RECORDING -> R.string.sheet_play_recording
            RowAction.PLAY -> R.string.sheet_play
        },
    )

    /** The tab's visible label. */
    fun tabLabel(tab: Tab, res: Resources): String = res.getString(
        when (tab) {
            Tab.ALL -> R.string.tab_all
            Tab.PHOTOS -> R.string.tab_photos
            Tab.VIDEOS -> R.string.tab_videos
            Tab.DOCUMENTS -> R.string.tab_documents
            Tab.OTHER_FILES -> R.string.tab_other_files
            Tab.CALLS -> R.string.tab_calls
            Tab.RECORDINGS -> R.string.tab_recordings
            Tab.MESSAGES -> R.string.tab_messages
        },
    )

    /** `{Type}, {count} items`, `{Type}, permission needed`, or just `{Type}` while the count is unknown. */
    fun tabDescription(tab: Tab, count: Int?, locked: Boolean, env: DeviceEnv, res: Resources): String {
        val label = tabLabel(tab, res)
        return when {
            locked -> res.getString(R.string.a11y_tab_permission, label)

            count == null -> label

            else -> res.getQuantityString(
                R.plurals.a11y_tab_count,
                count,
                label,
                NumberFormat.getIntegerInstance(env.locale).format(count),
            )
        }
    }

    private fun callNoun(direction: Direction?): Int = when (direction) {
        Direction.INCOMING -> R.string.a11y_call_incoming
        Direction.OUTGOING -> R.string.a11y_call_outgoing
        Direction.MISSED -> R.string.a11y_call_missed
        Direction.REJECTED -> R.string.a11y_call_rejected
        Direction.BLOCKED -> R.string.a11y_call_blocked
        else -> R.string.a11y_call_generic
    }

    private fun spoken(vararg parts: String?): String =
        parts.filterNot { it.isNullOrBlank() }.joinToString(SPOKEN_SEP) + "."
}
