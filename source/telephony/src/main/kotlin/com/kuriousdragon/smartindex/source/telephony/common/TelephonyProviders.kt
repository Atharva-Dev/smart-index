package com.kuriousdragon.smartindex.source.telephony.common

import android.content.Context
import android.database.Cursor
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import javax.inject.Singleton

/** One provider query: URI, projection, selection with bound `?` [args] and sort order. */
data class QuerySpec(
    val uri: Uri,
    val projection: List<String>,
    val selection: String? = null,
    val args: List<String> = emptyList(),
    val sortOrder: String? = null,
)

/** The seam between the indexers and the Android telephony providers (faked in tests). */
interface TelephonyProviders {
    /** Null when the provider is unavailable. Throws SecurityException when permission is missing. */
    fun query(spec: QuerySpec): Cursor?

    /** Reads at most [maxBytes] of a text part as UTF-8 (malformed → U+FFFD); null on any failure. */
    fun openText(uri: Uri, maxBytes: Int): String?
}

@Singleton
class ContentResolverProviders @Inject constructor(
    @ApplicationContext context: Context,
) : TelephonyProviders {
    private val resolver = context.contentResolver

    override fun query(spec: QuerySpec): Cursor? = resolver.query(
        spec.uri,
        spec.projection.toTypedArray(),
        spec.selection,
        spec.args.takeIf { it.isNotEmpty() }?.toTypedArray(),
        spec.sortOrder,
    )

    override fun openText(uri: Uri, maxBytes: Int): String? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val bytes = input.readNBytes(maxBytes) // API 33+: InputStream.readNBytes available
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }
    }.getOrNull()
}
