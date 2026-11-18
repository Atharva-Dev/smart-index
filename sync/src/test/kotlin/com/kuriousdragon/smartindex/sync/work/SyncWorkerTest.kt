package com.kuriousdragon.smartindex.sync.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.sync.SyncOutcome
import com.kuriousdragon.smartindex.sync.SyncReason
import com.kuriousdragon.smartindex.sync.SyncRunner
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun run(input: androidx.work.Data, runner: SyncRunner): ListenableWorker.Result {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = SyncWorker(appContext, workerParameters, runner)
        }
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setInputData(input)
            .setWorkerFactory(factory)
            .build()
        return runBlocking { worker.doWork() }
    }

    @Test
    fun `passes the input reason`() {
        val seen = mutableListOf<SyncReason>()
        val result = run(workDataOf(SyncWork.KEY_REASON to SyncReason.CATCH_UP.name)) {
            seen += it
            SyncOutcome()
        }
        assertThat(seen).containsExactly(SyncReason.CATCH_UP)
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `missing reason defaults to PERIODIC`() {
        val seen = mutableListOf<SyncReason>()
        run(workDataOf()) {
            seen += it
            SyncOutcome()
        }
        assertThat(seen).containsExactly(SyncReason.PERIODIC)
    }

    @Test
    fun `unknown reason defaults to PERIODIC`() {
        val seen = mutableListOf<SyncReason>()
        run(workDataOf(SyncWork.KEY_REASON to "BOGUS")) {
            seen += it
            SyncOutcome()
        }
        assertThat(seen).containsExactly(SyncReason.PERIODIC)
    }

    @Test
    fun `returns success when the runner throws`() {
        val result = run(workDataOf(SyncWork.KEY_REASON to SyncReason.OBSERVER.name)) {
            throw IllegalStateException("boom")
        }
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }
}
