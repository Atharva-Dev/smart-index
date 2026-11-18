package com.kuriousdragon.smartindex.start

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class OnboardingStoreTest {
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        dir = File.createTempFile("sidx_app", "dir").also {
            it.delete()
            it.mkdirs()
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun newStore() = OnboardingStore(
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "sidx_app.preferences_pb") },
    )

    @Test
    fun `completed is false by default`() = runBlocking {
        assertThat(newStore().completed.first()).isFalse()
    }

    @Test
    fun `markCompleted makes completed true`() = runBlocking {
        val store = newStore()
        store.markCompleted()
        assertThat(store.completed.first()).isTrue()
    }

    @Test
    fun `an IOException while reading is treated as not completed`() = runBlocking {
        val failing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("disk") }

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw IOException("disk")
        }
        assertThat(OnboardingStore(failing).completed.first()).isFalse()
    }
}
