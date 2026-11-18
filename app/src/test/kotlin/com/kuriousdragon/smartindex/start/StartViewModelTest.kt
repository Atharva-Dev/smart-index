package com.kuriousdragon.smartindex.start

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: OnboardingStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        dir = File.createTempFile("sidx_vm", "dir").also {
            it.delete()
            it.mkdirs()
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = OnboardingStore(
            PreferenceDataStoreFactory.create(scope = scope) { File(dir, "sidx_app.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `start destination is null before the first read then onboarding when not completed`() = runTest(dispatcher) {
        val vm = StartViewModel(store)
        assertThat(vm.startDestination.value).isNull()
        awaitValue(vm)
        assertThat(vm.startDestination.value).isEqualTo("onboarding")
    }

    @Test
    fun `start destination is home when onboarding was completed`() = runTest(dispatcher) {
        store.markCompleted()
        val vm = StartViewModel(store)
        assertThat(vm.startDestination.value).isNull()
        awaitValue(vm)
        assertThat(vm.startDestination.value).isEqualTo("home")
    }

    @Test
    fun `start destination stays fixed after the first value`() = runTest(dispatcher) {
        val vm = StartViewModel(store)
        awaitValue(vm)
        store.markCompleted()
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()
        assertThat(vm.startDestination.value).isEqualTo("onboarding")
    }

    private fun kotlinx.coroutines.test.TestScope.awaitValue(vm: StartViewModel) {
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.startDestination.value == null && System.currentTimeMillis() < deadline) {
            advanceUntilIdle()
            Thread.sleep(10)
        }
    }
}
