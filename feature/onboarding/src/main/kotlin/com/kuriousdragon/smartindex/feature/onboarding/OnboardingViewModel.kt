package com.kuriousdragon.smartindex.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexControl
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingEffect
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingPage
import com.kuriousdragon.smartindex.feature.onboarding.model.OnboardingUiState
import com.kuriousdragon.smartindex.feature.onboarding.model.PageFlow
import com.kuriousdragon.smartindex.feature.onboarding.model.buildSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

private const val TAG = "Onboarding"
private const val KEY_PAGE = "page"
private const val KEY_DIALOG = "dialog"
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * State holder of the onboarding flow (LLD §3.2, §5.1–§5.3).
 *
 * [permissionService] is public only so the route can build `rememberGroupRequester`; nothing else reads it
 * (LLD §6.3).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val permissionService: PermissionService,
    private val indexControl: IndexControl,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val page = MutableStateFlow(PageFlow.decode(savedState.get<Int?>(KEY_PAGE)))
    private val dialog = MutableStateFlow(restoreDialog())
    private val isStarting = MutableStateFlow(false)

    val uiState: StateFlow<OnboardingUiState> =
        combine(page, dialog, permissionService.state, isStarting, ::build)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = build(page.value, dialog.value, permissionService.state.value, isStarting.value),
            )

    private val effectChannel = Channel<OnboardingEffect>(Channel.BUFFERED)

    /** One-shot effects (run the group requester, finish onboarding). */
    val effects: Flow<OnboardingEffect> = effectChannel.receiveAsFlow()

    fun onGetStarted() {
        if (page.value != OnboardingPage.Welcome) return
        setPage(PageFlow.next(OnboardingPage.Welcome, states()))
    }

    fun onAllowClick(group: Group) {
        if (page.value != OnboardingPage.Step(group)) return
        if (states()[group] == GroupState.DENIED_PERMANENTLY) {
            SiLog.i(TAG, "settings dialog group=${group.name}")
            setDialog(group)
        } else {
            SiLog.i(TAG, "request group=${group.name}")
            effectChannel.trySend(OnboardingEffect.RequestGroup(group))
        }
    }

    fun onGroupResult(
        group: Group,
        state: GroupState,
    ) {
        val current = page.value
        if (current != OnboardingPage.Step(group)) return
        SiLog.i(TAG, "result group=${group.name} state=${state.name}")
        setPage(PageFlow.next(current, states()))
    }

    fun onSkip() {
        val current = page.value
        if (current !is OnboardingPage.Step) return
        SiLog.i(TAG, "skip group=${current.group.name}")
        setPage(PageFlow.next(current, states()))
    }

    fun onBack(): Boolean {
        if (dialog.value != null) {
            setDialog(null)
            return true
        }
        val current = page.value
        if (current == OnboardingPage.Welcome) return false
        setPage(PageFlow.previous(current, states()) ?: OnboardingPage.Welcome)
        return true
    }

    fun onStartIndexing() {
        if (page.value != OnboardingPage.Summary || isStarting.value) return
        isStarting.value = true
        SiLog.i(TAG, "start indexing page=${PageFlow.encode(page.value)}")
        indexControl.reindexAll()
        effectChannel.trySend(OnboardingEffect.Finish)
    }

    fun onSettingsDialogCancel() {
        setDialog(null)
    }

    fun onSettingsDialogOpenSettings() {
        val group = dialog.value ?: return
        setDialog(null)
        SiLog.i(TAG, "open settings group=${group.name}")
        effectChannel.trySend(OnboardingEffect.RequestGroup(group))
    }

    private fun states(): Map<Group, GroupState> = permissionService.state.value

    private fun setPage(value: OnboardingPage) {
        page.value = value
        savedState[KEY_PAGE] = PageFlow.encode(value)
        SiLog.i(TAG, "page=${PageFlow.encode(value)}")
    }

    private fun setDialog(value: Group?) {
        dialog.value = value
        savedState[KEY_DIALOG] = value?.name
    }

    private fun restoreDialog(): Group? {
        val raw = savedState.get<String?>(KEY_DIALOG) ?: return null
        val group = Group.entries.firstOrNull { it.name == raw }
        return if (group == null || group == Group.FILES) null else group
    }

    private fun build(
        page: OnboardingPage,
        dialog: Group?,
        states: Map<Group, GroupState>,
        starting: Boolean,
    ) = OnboardingUiState(
        page = page,
        groupStates = states,
        summary = buildSummary(states),
        settingsDialog = dialog,
        isStarting = starting,
    )
}
