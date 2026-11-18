package com.kuriousdragon.smartindex.feature.home.format

import android.content.res.Resources
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.Field
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv
import java.util.Locale

internal fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }

/** Pure `ResultItem` to [RowUi] mapping (LLD 4.5). Cheap and side-effect free: it runs per visible row. */
object RowFormatter {
    private const val META_SEP = " · "
    private const val MAX_EXT_LABEL = 5

    fun format(item: ResultItem, env: DeviceEnv, res: Resources): RowUi {
        val isFile = item.type in FILE_TYPES
        val title = title(item)
        val (meta, metaIcon) = meta(item, env, res)
        val snippet = if (item.type == ItemType.MESSAGE) snippet(item, res) else null
        return RowUi(
            id = item.id,
            type = item.type,
            direction = item.direction,
            title = title,
            titleEllipsis = if (isFile) TitleEllipsis.MIDDLE else TitleEllipsis.END,
            time = TimeLabels.format(item.time, env),
            meta = meta,
            metaIcon = metaIcon,
            showAttachment = item.type == ItemType.MESSAGE && item.hasAttachment,
            snippet = snippet,
            extLabel = if (item.type in EXT_LABEL_TYPES) extLabel(item) else null,
            videoDuration = if (item.type == ItemType.VIDEO) item.durationMs?.let(DurationFormat::media) else null,
            trailing = trailing(item),
            recordingId = when (item.type) {
                ItemType.RECORDING -> item.id
                ItemType.CALL -> item.linkedRecordingId
                else -> null
            },
            a11yLabel = A11yLabels.rowLabel(item, env, res),
            actions = A11yLabels.actions(item),
        )
    }

    private fun title(item: ResultItem): HighlightedText {
        val ranges = { field: Field -> item.matches[field].orEmpty() }
        val contact = item.contactName.nonBlank()
        val number = item.number.nonBlank()
        return when (item.type) {
            ItemType.CALL, ItemType.MESSAGE ->
                if (contact != null) {
                    HighlightedText(contact, clean(ranges(Field.CONTACT_NAME), contact))
                } else {
                    HighlightedText(item.title, clean(ranges(Field.TITLE), item.title))
                }

            ItemType.RECORDING -> when {
                contact != null -> HighlightedText(contact, clean(ranges(Field.CONTACT_NAME), contact))
                number != null -> HighlightedText(number, clean(ranges(Field.NUMBER), number))
                else -> fileName(item)
            }

            else -> fileName(item)
        }
    }

    /** File name with its TITLE matches plus the EXT matches mapped onto the title tail (dropped if it does not end in the ext). */
    private fun fileName(item: ResultItem): HighlightedText {
        val title = item.title
        val own = item.matches[Field.TITLE].orEmpty()
        val ext = item.ext.nonBlank()?.trimStart('.')
        val extRanges = item.matches[Field.EXT].orEmpty()
        val mapped = if (ext != null && extRanges.isNotEmpty() && title.endsWith(".$ext", ignoreCase = true)) {
            Highlight.shift(extRanges, title.length - ext.length, title.length)
        } else {
            emptyList()
        }
        return HighlightedText(title, clean(own + mapped, title))
    }

    private fun meta(
        item: ResultItem,
        env: DeviceEnv,
        res: Resources,
    ): Pair<HighlightedText, MetaIcon?> = when (item.type) {
        ItemType.PHOTO, ItemType.DOCUMENT, ItemType.OTHER_FILE -> fileMeta(item, env, includeDuration = false)

        ItemType.VIDEO -> fileMeta(item, env, includeDuration = true)

        ItemType.CALL -> HighlightedText(callMeta(item, res)) to callIcon(item.direction)

        ItemType.RECORDING -> {
            val text = item.durationMs?.let { res.getString(R.string.rec_meta, DurationFormat.media(it)) }
                ?: res.getString(R.string.rec_meta_nodur)
            HighlightedText(text) to MetaIcon.RECORDING
        }

        ItemType.MESSAGE -> HighlightedText(messageMeta(item, res)) to when (item.direction) {
            Direction.RECEIVED -> MetaIcon.SMS_RECEIVED
            Direction.SENT -> MetaIcon.SMS_SENT
            else -> null
        }
    }

    private fun fileMeta(
        item: ResultItem,
        env: DeviceEnv,
        includeDuration: Boolean,
    ): Pair<HighlightedText, MetaIcon?> {
        val segment = folderSegment(item.folder)
        val parts = listOfNotNull(
            segment?.first,
            if (includeDuration) item.durationMs?.let(DurationFormat::media) else null,
            item.sizeBytes?.let { SizeFormat.format(it, env.locale) },
        )
        // The folder segment is always the first part, so its shifted ranges need no further offset.
        val ranges = if (segment != null) {
            Highlight.shift(item.matches[Field.FOLDER].orEmpty(), -segment.second, segment.first.length)
        } else {
            emptyList()
        }
        val text = parts.joinToString(META_SEP)
        return HighlightedText(text, Highlight.merge(Highlight.clamp(ranges, text.length))) to null
    }

    /** Last path segment of [folder] and the number of characters cut off in front of it; null when blank. */
    internal fun folderSegment(folder: String?): Pair<String, Int>? {
        val trimmed = folder?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return null
        val segment = trimmed.substringAfterLast('/')
        return if (segment.isBlank()) null else segment to (trimmed.length - segment.length)
    }

    private fun callMeta(item: ResultItem, res: Resources): String {
        val duration = item.durationMs?.let { DurationFormat.call(it, res) }
        return when (item.direction) {
            Direction.INCOMING ->
                duration?.let { res.getString(R.string.call_in, it) } ?: res.getString(R.string.call_in_nodur)

            Direction.OUTGOING ->
                duration?.let { res.getString(R.string.call_out, it) } ?: res.getString(R.string.call_out_nodur)

            Direction.MISSED -> res.getString(R.string.call_missed)

            Direction.REJECTED -> res.getString(R.string.call_rejected)

            Direction.BLOCKED -> res.getString(R.string.call_blocked)

            else -> ""
        }
    }

    private fun callIcon(direction: Direction?): MetaIcon? = when (direction) {
        Direction.INCOMING -> MetaIcon.INCOMING
        Direction.OUTGOING -> MetaIcon.OUTGOING
        Direction.MISSED -> MetaIcon.MISSED
        Direction.REJECTED -> MetaIcon.REJECTED
        Direction.BLOCKED -> MetaIcon.BLOCKED
        else -> null
    }

    private fun messageMeta(item: ResultItem, res: Resources): String {
        val mms = item.msgKind == MsgKind.MMS
        return when (item.direction) {
            Direction.RECEIVED -> res.getString(if (mms) R.string.msg_in_mms else R.string.msg_in)
            Direction.SENT -> res.getString(if (mms) R.string.msg_out_mms else R.string.msg_out)
            else -> res.getString(if (mms) R.string.msg_mms else R.string.msg_sms)
        }
    }

    private fun snippet(item: ResultItem, res: Resources): HighlightedText {
        val text = item.snippet
        return if (text.isNullOrEmpty()) {
            HighlightedText(res.getString(R.string.msg_no_text))
        } else {
            HighlightedText(text, clean(item.matches[Field.SNIPPET].orEmpty(), text))
        }
    }

    private fun trailing(item: ResultItem): Trailing = when (item.type) {
        ItemType.PHOTO, ItemType.VIDEO, ItemType.DOCUMENT, ItemType.OTHER_FILE -> Trailing.MORE
        ItemType.RECORDING -> Trailing.PLAY
        ItemType.CALL -> if (item.linkedRecordingId != null) Trailing.PLAY else Trailing.NONE
        ItemType.MESSAGE -> Trailing.NONE
    }

    private fun extLabel(item: ResultItem): String? {
        val raw = item.ext.nonBlank() ?: item.title.substringAfterLast('.', "").takeIf { it.isNotBlank() }
        return raw?.trimStart('.')?.uppercase(Locale.ROOT)?.take(MAX_EXT_LABEL)?.takeIf { it.isNotEmpty() }
    }

    private fun clean(ranges: List<IntRange>, text: String): List<IntRange> =
        Highlight.merge(Highlight.clamp(ranges, text.length))

    private val FILE_TYPES = setOf(ItemType.PHOTO, ItemType.VIDEO, ItemType.DOCUMENT, ItemType.OTHER_FILE)
    private val EXT_LABEL_TYPES = setOf(ItemType.DOCUMENT, ItemType.OTHER_FILE, ItemType.RECORDING)
}
