package com.kuriousdragon.smartindex.feature.home.format

import android.content.res.Resources
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv

/** Mini-player title and subtitle (Design Spec 6.7). */
object PlayerLabels {
    /** Call: contact name or title; recording: contact name, number or title. */
    fun title(item: ResultItem): String = when (item.type) {
        ItemType.RECORDING -> item.contactName.nonBlank() ?: item.number.nonBlank() ?: item.title
        else -> item.contactName.nonBlank() ?: item.title
    }

    /** `<Incoming / Outgoing / Missed call> · <EEE d MMM, time>`; a recording uses `Call recording · …`. */
    fun subtitle(item: ResultItem, env: DeviceEnv, res: Resources): String {
        val stamp = DateLabels.stamp(item.time, env)
        val template = when {
            item.type == ItemType.RECORDING -> R.string.player_sub_recording
            item.direction == Direction.INCOMING -> R.string.player_sub_incoming
            item.direction == Direction.OUTGOING -> R.string.player_sub_outgoing
            item.direction == Direction.MISSED -> R.string.player_sub_missed
            item.direction == Direction.REJECTED -> R.string.player_sub_rejected
            item.direction == Direction.BLOCKED -> R.string.player_sub_blocked
            else -> R.string.player_sub_call
        }
        return res.getString(template, stamp)
    }
}
