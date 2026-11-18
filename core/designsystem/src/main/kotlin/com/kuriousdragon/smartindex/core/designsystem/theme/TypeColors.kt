package com.kuriousdragon.smartindex.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemType

/** Icon and tile colour of one item type (Design Spec §5.2). */
data class TypeColors(val icon: Color, val tile: Color)

enum class TypeColorKey { PHOTO, VIDEO, DOC, FILE, CALL, MISSED, REC, MSG }

internal val LightTypeColors: Map<TypeColorKey, TypeColors> = mapOf(
    TypeColorKey.PHOTO to TypeColors(Color(0xFF7A3EB1), Color(0xFFF1E6FB)),
    TypeColorKey.VIDEO to TypeColors(Color(0xFFB5400B), Color(0xFFFDE7DC)),
    TypeColorKey.DOC to TypeColors(Color(0xFF1F5FBF), Color(0xFFE1EBFB)),
    TypeColorKey.FILE to TypeColors(Color(0xFF4E6470), Color(0xFFE6ECEF)),
    TypeColorKey.CALL to TypeColors(Color(0xFF25723A), Color(0xFFDFF1E3)),
    TypeColorKey.MISSED to TypeColors(Color(0xFFBA1A1A), Color(0xFFFFE4E1)),
    TypeColorKey.REC to TypeColors(Color(0xFFA3195B), Color(0xFFFCE3EE)),
    TypeColorKey.MSG to TypeColors(Color(0xFF00696B), Color(0xFFD9F2F1)),
)

internal val DarkTypeColors: Map<TypeColorKey, TypeColors> = mapOf(
    TypeColorKey.PHOTO to TypeColors(Color(0xFFDDB8FF), Color(0xFF3B2052)),
    TypeColorKey.VIDEO to TypeColors(Color(0xFFFFB693), Color(0xFF4F2410)),
    TypeColorKey.DOC to TypeColors(Color(0xFFADC6FF), Color(0xFF1B3360)),
    TypeColorKey.FILE to TypeColors(Color(0xFFB4CAD5), Color(0xFF2A363C)),
    TypeColorKey.CALL to TypeColors(Color(0xFF8DD89E), Color(0xFF16371F)),
    TypeColorKey.MISSED to TypeColors(Color(0xFFFFB4AB), Color(0xFF5C1712)),
    TypeColorKey.REC to TypeColors(Color(0xFFFFB0CF), Color(0xFF4E1730)),
    TypeColorKey.MSG to TypeColors(Color(0xFF7FD5D3), Color(0xFF0F3938)),
)

fun typeColorKey(type: ItemType, direction: Direction?): TypeColorKey = when (type) {
    ItemType.PHOTO -> TypeColorKey.PHOTO
    ItemType.VIDEO -> TypeColorKey.VIDEO
    ItemType.DOCUMENT -> TypeColorKey.DOC
    ItemType.OTHER_FILE -> TypeColorKey.FILE
    ItemType.CALL -> if (direction == Direction.MISSED) TypeColorKey.MISSED else TypeColorKey.CALL
    ItemType.RECORDING -> TypeColorKey.REC
    ItemType.MESSAGE -> TypeColorKey.MSG
}
