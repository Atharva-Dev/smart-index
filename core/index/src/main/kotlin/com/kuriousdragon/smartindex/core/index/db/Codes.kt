package com.kuriousdragon.smartindex.core.index.db

import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.Source

/** Stable integer codes stored in the database (LLD §4.3). Never use enum ordinals. */

fun ItemType.code(): Int =
    when (this) {
        ItemType.PHOTO -> 1
        ItemType.VIDEO -> 2
        ItemType.DOCUMENT -> 3
        ItemType.OTHER_FILE -> 4
        ItemType.CALL -> 5
        ItemType.RECORDING -> 6
        ItemType.MESSAGE -> 7
    }

fun itemType(code: Int): ItemType =
    when (code) {
        1 -> ItemType.PHOTO
        2 -> ItemType.VIDEO
        3 -> ItemType.DOCUMENT
        4 -> ItemType.OTHER_FILE
        5 -> ItemType.CALL
        6 -> ItemType.RECORDING
        7 -> ItemType.MESSAGE
        else -> badCode()
    }

fun Source.code(): Int =
    when (this) {
        Source.MEDIA_STORE -> 1
        Source.CALL_LOG -> 2
        Source.SMS -> 3
        Source.MMS -> 4
    }

fun source(code: Int): Source =
    when (code) {
        1 -> Source.MEDIA_STORE
        2 -> Source.CALL_LOG
        3 -> Source.SMS
        4 -> Source.MMS
        else -> badCode()
    }

fun Direction.code(): Int =
    when (this) {
        Direction.INCOMING -> 1
        Direction.OUTGOING -> 2
        Direction.MISSED -> 3
        Direction.REJECTED -> 4
        Direction.BLOCKED -> 5
        Direction.SENT -> 6
        Direction.RECEIVED -> 7
    }

fun direction(code: Int): Direction =
    when (code) {
        1 -> Direction.INCOMING
        2 -> Direction.OUTGOING
        3 -> Direction.MISSED
        4 -> Direction.REJECTED
        5 -> Direction.BLOCKED
        6 -> Direction.SENT
        7 -> Direction.RECEIVED
        else -> badCode()
    }

fun MsgKind.code(): Int =
    when (this) {
        MsgKind.SMS -> 1
        MsgKind.MMS -> 2
    }

fun msgKind(code: Int): MsgKind =
    when (code) {
        1 -> MsgKind.SMS
        2 -> MsgKind.MMS
        else -> badCode()
    }

fun DateKind.code(): Int =
    when (this) {
        DateKind.TAKEN -> 1
        DateKind.ADDED -> 2
        DateKind.MODIFIED -> 3
        DateKind.EVENT -> 4
    }

fun dateKind(code: Int): DateKind =
    when (code) {
        1 -> DateKind.TAKEN
        2 -> DateKind.ADDED
        3 -> DateKind.MODIFIED
        4 -> DateKind.EVENT
        else -> badCode()
    }

private fun badCode(): Nothing = throw IllegalStateException("bad code")
