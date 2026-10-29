package com.trackgas.app

import android.os.Bundle
import android.accessibilityservice.AccessibilityService
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.trackgas.app.tracking.*
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ExportLifecycleTest {
    @Test fun cancellingActualPickerAfterActivityRecreationDoesNotCompleteExport() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<TrackGasApplication>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var tracker: TrackerViewModel
        lateinit var activity: MainActivity
        try {
            scenario.onActivity { current ->
                activity = current
                val saved = Bundle()
                InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(current, saved)
                val id = saved.getString("tracker_id")!!
                val store = application.trackerStoreOwner.obtain(id, current.taskId, setOf(current.taskId))
                tracker = ViewModelProvider(store, TrackerViewModel.factory(application.readingRepository))[TrackerViewModel::class.java]
            }
            withContext(Dispatchers.Main) {
                withTimeout(5_000) { snapshotFlow { tracker.state.loadState }.first { it == ReadingLoadState.READY } }
            }
            scenario.onActivity { it.startExport() }
            withContext(Dispatchers.Main) {
                assertTrue(tracker.state.export is ExportState.Selecting)
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.recreate() }
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            withContext(Dispatchers.Main) {
                withTimeout(10_000) { snapshotFlow { tracker.state.export }.first { it == ExportState.Idle } }
            }
            scenario.onActivity { assertNotSame(activity, it) }
            scenario.recreate()
            scenario.onActivity { assertEquals(ExportState.Idle, tracker.state.export) }
        } finally {
            scenario.close()
        }
        Unit
    }

    @Test fun selectingAndWritingSurviveRecreationAndLostRequestCannotSucceed() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<TrackGasApplication>()
        val data = TrackerData(listOf(Reading("reading", LocalDate.of(2026, 10, 28), "123.450".toBigDecimal())))
        val repository = object : ReadingRepository {
            override fun observeReadings() = MutableStateFlow(CommittedSnapshot(1, data))
            override suspend fun save(intent: SaveReadingIntent): SaveReadingResult = error("Unexpected save")
            override suspend fun delete(id: String): DeleteReadingResult = error("Unexpected deletion")
            override suspend fun replaceMeter(intent: ReplaceMeterIntent): ReplaceMeterResult = error("Unexpected replacement")
            override suspend fun purge(): CommittedSnapshot = error("Unexpected purge")
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val opens = AtomicInteger()
        val output = ByteArrayOutputStream()
        lateinit var tracker: TrackerViewModel
        var id = ""
        var taskId = 0
        try {
            scenario.onActivity { activity ->
                val saved = Bundle()
                InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(activity, saved)
                id = saved.getString("tracker_id")!!
                taskId = activity.taskId
                val store = application.trackerStoreOwner.obtain(id, taskId, setOf(taskId))
                store.clear()
                tracker = ViewModelProvider(store, TrackerViewModel.factory(repository))[TrackerViewModel::class.java]
            }
            scenario.recreate()
            val request = withContext(Dispatchers.Main) {
                withTimeout(5_000) { snapshotFlow { tracker.state.loadState }.first { it == ReadingLoadState.READY } }
                requireNotNull(tracker.requestExport())
            }
            scenario.recreate()
            scenario.onActivity {
                val retained = ViewModelProvider(
                    application.trackerStoreOwner.obtain(id, taskId, setOf(taskId)),
                    TrackerViewModel.factory(repository),
                )[TrackerViewModel::class.java]
                assertSame(tracker, retained)
                assertEquals(request, (retained.state.export as ExportState.Selecting).requestId)
                assertNull(retained.requestExport())
                retained.writeExport(request) {
                    opens.incrementAndGet()
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    output
                }
                retained.writeExport(request) { error("Duplicate write") }
            }
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            scenario.recreate()
            scenario.onActivity {
                assertTrue(tracker.state.export is ExportState.Writing)
                assertNull(tracker.requestExport())
            }
            release.countDown()
            withContext(Dispatchers.Main) {
                withTimeout(5_000) { snapshotFlow { tracker.state.export }.first { it is ExportState.Complete } }
            }
            assertEquals(1, opens.get())
            assertEquals("meter_id;date;meter_value\noriginal;2026-10-28;123.450\n", output.toString("UTF-8"))
            scenario.recreate()
            scenario.onActivity {
                assertEquals(ExportState.Complete(request, true), tracker.state.export)
                tracker.acknowledgeExport(request)
                tracker.finishExportWrite(request, true)
                assertEquals(ExportState.Idle, tracker.state.export)
            }
            scenario.recreate()
            scenario.onActivity { assertEquals(ExportState.Idle, tracker.state.export) }
            withContext(Dispatchers.Main) {
                val freshOwner = TrackerStoreOwner()
                try {
                    val fresh = ViewModelProvider(
                        freshOwner.obtain(id, taskId, emptySet()), TrackerViewModel.factory(repository),
                    )[TrackerViewModel::class.java]
                    fresh.finishExportWrite(request, true)
                    assertEquals(ExportState.Idle, fresh.state.export)
                } finally {
                    freshOwner.release(id)
                }
            }
        } finally {
            release.countDown()
            scenario.close()
        }
    }
}
