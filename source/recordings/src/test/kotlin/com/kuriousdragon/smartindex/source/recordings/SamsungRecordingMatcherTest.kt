package com.kuriousdragon.smartindex.source.recordings

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.RecordingInputs
import com.kuriousdragon.smartindex.source.recordings.diag.RecordingDiagnostics
import com.kuriousdragon.smartindex.source.recordings.parse.RecordingNameParser
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SamsungRecordingMatcherTest {
    private var zone: ZoneId = IST

    private fun TestScope.matcher() =
        SamsungRecordingMatcher(
            numbers = numberNormalizer,
            parser = RecordingNameParser(),
            zoneProvider = { zone },
            diagnostics = RecordingDiagnostics.None,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

    // Modified time is far from the call so that only the parsed (zone dependent) anchor can link.
    private val inputs =
        RecordingInputs(
            calls = listOf(call(1, "2024-11-03T09:41:58", 185)),
            recordings =
                listOf(recording(11, "Call recording Rahul_241103_094215.m4a", 184, "2024-11-03T20:00:00")),
            links = emptyList(),
        )

    @Test
    fun `runs on the injected dispatcher`() =
        runTest {
            val deferred = async { matcher().match(inputs) }
            testScheduler.runCurrent()
            assertThat(deferred.isCompleted).isTrue()
            assertThat(deferred.await().map { it.callItemId to it.recordingItemId }).containsExactly(1L to 11L)
        }

    @Test
    fun `work is dispatched, not run inline`() =
        runTest {
            val m = matcher()
            val deferred = async(start = CoroutineStart.UNDISPATCHED) { m.match(inputs) }
            assertThat(deferred.isCompleted).isFalse()
            testScheduler.advanceUntilIdle()
            assertThat(deferred.await()).hasSize(1)
        }

    @Test
    fun `cancellation before completion throws CancellationException and returns nothing`() =
        runTest {
            val deferred = async(start = CoroutineStart.UNDISPATCHED) { matcher().match(inputs) }
            deferred.cancel()
            testScheduler.advanceUntilIdle()
            val outcome = runCatching { deferred.await() }
            assertThat(outcome.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        }

    @Test
    fun `empty inputs give an empty list`() =
        runTest {
            val deferred = async { matcher().match(RecordingInputs(emptyList(), emptyList(), emptyList())) }
            testScheduler.advanceUntilIdle()
            assertThat(deferred.await()).isEmpty()
        }

    @Test
    fun `the zone is read on every call`() =
        runTest {
            val m = matcher()
            zone = IST
            val first = async { m.match(inputs) }
            testScheduler.advanceUntilIdle()
            assertThat(first.await()).hasSize(1)

            zone = LONDON
            val second = async { m.match(inputs) }
            testScheduler.advanceUntilIdle()
            assertThat(second.await()).isEmpty()

            zone = IST
            val third = async { m.match(inputs) }
            testScheduler.advanceUntilIdle()
            assertThat(third.await()).hasSize(1)
        }
}
