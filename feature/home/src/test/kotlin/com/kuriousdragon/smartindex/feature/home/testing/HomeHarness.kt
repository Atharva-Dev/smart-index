package com.kuriousdragon.smartindex.feature.home.testing

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.kuriousdragon.smartindex.feature.home.HomeViewModel
import com.kuriousdragon.smartindex.feature.home.IntentLauncher
import com.kuriousdragon.smartindex.feature.home.action.ItemActions
import com.kuriousdragon.smartindex.feature.home.model.HomeClock
import com.kuriousdragon.smartindex.feature.home.prefs.HomePrefs
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * A [HomeViewModel] wired with the fakes of this package for Compose tests (no Hilt). `Dispatchers.Main` and the
 * ViewModel's default dispatcher are one unconfined test dispatcher on [scheduler], so the 300 ms debounce is
 * advanced with [advance] while the compose test clock is advanced separately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HomeHarness(storeDir: File, now: Long = 1_800_000_000_000L) {
    val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private val storeScope = CoroutineScope(dispatcher + SupervisorJob())

    val index = FakeIndexQuery()
    val status = FakeIndexStatusSource()
    val perms = FakePermissionService()
    val envSource = FakeDeviceEnvSource()
    val player = FakeRecordingPlayer()
    val resolver = FakeItemUriResolver()
    val control = FakeIndexControl()
    val contacts = FakeContactLookup()
    val launched = mutableListOf<Intent>()

    /** Intents matching this predicate throw `ActivityNotFoundException` from the launcher. */
    var unresolvable: (Intent) -> Boolean = { false }

    val launcher = IntentLauncher { intent ->
        launched += intent
        if (unresolvable(intent)) throw android.content.ActivityNotFoundException()
    }

    var changingConfigurations = false

    private val actions = ItemActions(resolver, index, contacts, control)
    private val clock = HomeClock { now }
    private val dataStore = PreferenceDataStoreFactory.create(scope = storeScope) {
        File(storeDir, "sidx_app.preferences_pb")
    }

    init {
        Dispatchers.setMain(dispatcher)
    }

    val viewModel: HomeViewModel by lazy {
        HomeViewModel(
            index, status, perms, envSource, HomePrefs(dataStore, clock), actions, player, clock,
            ApplicationProvider.getApplicationContext<Context>(), dispatcher, SavedStateHandle(),
        )
    }

    /** Advances the ViewModel's virtual time (debounce) and runs what became due. */
    fun advance(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    fun close() {
        storeScope.cancel()
        Dispatchers.resetMain()
    }
}
