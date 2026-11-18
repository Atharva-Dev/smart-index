package com.kuriousdragon.smartindex.feature.home.state

import android.content.Intent
import androidx.annotation.StringRes
import com.kuriousdragon.smartindex.core.model.Group

sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plain(val text: String) : UiText
}

/** One-shot instructions from the ViewModel to the UI. */
sealed interface HomeEffect {
    /**
     * Try [attempts] in order with startActivity; the first that does not throw ActivityNotFoundException
     * wins; none: snackbar [noHandler].
     */
    data class Launch(val attempts: List<Intent>, val noHandler: UiText) : HomeEffect

    /** Snackbar; [long] = has an action or is an error (10 s). */
    data class ShowMessage(val text: UiText, val long: Boolean = false) : HomeEffect

    /** The UI calls the rememberGroupRequester launcher. */
    data class RequestPermission(val group: Group) : HomeEffect

    /** "permissions" or null. */
    data class OpenStatus(val section: String?) : HomeEffect
}
