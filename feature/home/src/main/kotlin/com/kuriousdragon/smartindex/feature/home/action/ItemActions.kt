package com.kuriousdragon.smartindex.feature.home.action

import com.kuriousdragon.smartindex.core.model.IndexControl
import com.kuriousdragon.smartindex.core.model.IndexQuery
import com.kuriousdragon.smartindex.core.model.ItemUriResolver
import com.kuriousdragon.smartindex.core.model.OpenTarget
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.TypeGating
import com.kuriousdragon.smartindex.core.model.resolve
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.state.HomeEffect
import com.kuriousdragon.smartindex.feature.home.state.UiText
import javax.inject.Inject

/** Turns a row or sheet action into a [HomeEffect] (LLD §3.6, §5.3-§5.5). Logs ids and enum names only. */
class ItemActions @Inject constructor(
    private val resolver: ItemUriResolver,
    private val index: IndexQuery,
    private val contacts: ContactLookup,
    private val control: IndexControl,
) {
    suspend fun open(item: ResultItem, chooser: Boolean): HomeEffect {
        val target = resolver.resolve(item)
        SiLog.d(TAG, "open resolved=${target.logName()} type=${item.type}")
        return when (target) {
            is OpenTarget.Ready -> {
                val view = ItemIntents.view(target.uri, target.mimeType)
                HomeEffect.Launch(
                    attempts = listOf(if (chooser) ItemIntents.chooser(view) else view),
                    noHandler = noAppText(item.ext),
                )
            }

            OpenTarget.Missing -> {
                control.catchUpNow()
                fileGone()
            }

            OpenTarget.NoAccess -> HomeEffect.RequestPermission(TypeGating.groupOf(item.type))

            OpenTarget.NotAFile -> fileGone()
        }
    }

    suspend fun openMessage(item: ResultItem): HomeEffect {
        val detail = index.item(item.id)
        val attempts = buildList {
            detail?.threadId?.let { add(ItemIntents.smsThread(it)) }
            detail?.numberRaw?.takeIf { it.isNotBlank() }?.let { add(ItemIntents.smsTo(it)) }
        }
        val failure = UiText.Res(R.string.err_message_open)
        return if (attempts.isEmpty()) {
            HomeEffect.ShowMessage(failure, long = true)
        } else {
            HomeEffect.Launch(attempts, noHandler = failure)
        }
    }

    fun callBack(item: ResultItem): HomeEffect? {
        val number = item.number?.takeIf { it.isNotBlank() } ?: return null
        return HomeEffect.Launch(listOf(ItemIntents.dial(number)), noHandler = UiText.Res(R.string.err_call_open))
    }

    suspend fun openContact(item: ResultItem): HomeEffect {
        val number = item.number?.takeIf { it.isNotBlank() }
            ?: return HomeEffect.ShowMessage(UiText.Res(R.string.err_contact_open), long = true)
        val uri = contacts.lookupUri(number)
        SiLog.d(TAG, "openContact hit=${uri != null}")
        val intent = if (uri != null) ItemIntents.openContact(uri) else ItemIntents.addContact(number)
        return HomeEffect.Launch(listOf(intent), noHandler = UiText.Res(R.string.err_contact_open))
    }

    fun addContact(item: ResultItem): HomeEffect? {
        val number = item.number?.takeIf { it.isNotBlank() } ?: return null
        return HomeEffect.Launch(
            listOf(ItemIntents.addContact(number)),
            noHandler = UiText.Res(R.string.err_contact_open),
        )
    }

    private fun fileGone(): HomeEffect = HomeEffect.ShowMessage(UiText.Res(R.string.err_file_gone), long = true)

    private fun noAppText(ext: String?): UiText =
        if (ext.isNullOrBlank()) {
            UiText.Res(R.string.err_no_app_noext)
        } else {
            UiText.Res(R.string.err_no_app, listOf(ext))
        }

    private fun OpenTarget.logName(): String = this::class.simpleName.orEmpty()

    private companion object {
        const val TAG = "Home.Act"
    }
}
