package com.kuriousdragon.smartindex.source.media.open

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.kuriousdragon.smartindex.core.model.MediaContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** Seam so the resolver is unit-testable without a FileProvider (LLD — Media & file indexer §3.3.6). */
fun interface FileUriFactory {
    /** `content://<pkg>.files/…` for [file]; throws [IllegalArgumentException] when no paths root covers it. */
    fun uriFor(file: File): Uri
}

/** [FileUriFactory] over the `:app` FileProvider (authority `<applicationId>.files`, non-exported). */
class FileProviderUris @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileUriFactory {
    override fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, MediaContract.fileProviderAuthority(context.packageName), file)
}
