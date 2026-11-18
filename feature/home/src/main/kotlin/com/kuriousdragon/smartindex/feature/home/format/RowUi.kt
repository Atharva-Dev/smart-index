package com.kuriousdragon.smartindex.feature.home.format

import androidx.compose.runtime.Immutable
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemType

/** Where a long title is cut: file names keep their extension (MIDDLE), names and numbers cut at the END. */
enum class TitleEllipsis { MIDDLE, END }

/** What the 48 x 48 trailing slot holds. */
enum class Trailing { MORE, PLAY, NONE }

/** Leading 16 dp icon of the meta line. */
enum class MetaIcon { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, RECORDING, SMS_RECEIVED, SMS_SENT }

/** Accessibility custom actions of a row (LLD 4.10). */
enum class RowAction {
    OPEN_WITH,
    MORE_ACTIONS,
    CALL_BACK,
    OPEN_CONTACT,
    ADD_CONTACT,
    PLAY_RECORDING,
    PLAY,
    OPEN_WITH_AUDIO,
}

/** Everything a list row shows, as plain text and ranges (never colours). Built by [RowFormatter]. */
@Immutable
data class RowUi(
    val id: Long,
    val type: ItemType,
    val direction: Direction?,
    val title: HighlightedText,
    val titleEllipsis: TitleEllipsis, // MIDDLE | END
    val time: String,
    val meta: HighlightedText,
    val metaIcon: MetaIcon?,
    val showAttachment: Boolean,
    val snippet: HighlightedText?,
    val extLabel: String?, // uppercase, at most 5 chars, for type tiles
    val videoDuration: String?, // m:ss pill text for VIDEO
    val trailing: Trailing, // MORE | PLAY | NONE
    val recordingId: Long?, // PLAY target: the item itself for RECORDING, linkedRecordingId for CALL
    val a11yLabel: String,
    val actions: List<RowAction>,
)
