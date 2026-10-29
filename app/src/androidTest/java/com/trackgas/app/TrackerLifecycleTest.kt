package com.trackgas.app

import android.os.Bundle
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.trackgas.app.tracking.*
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class TrackerLifecycleTest {
    @Test fun pendingReplacementSurvivesRecreationAndFreshSessionDoesNotRestoreOrResubmitIt() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<TrackGasApplication>()
        val date = LocalDate.now()
        val final = Reading("lifecycle-final", date, "1105".toBigDecimal())
        val initial = TrackerData(listOf(final))
        val committed = TrackerData(
            listOf(final, Reading("lifecycle-baseline", date, "0".toBigDecimal(), "lifecycle-new")),
            listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID), MeterInstallation("lifecycle-new", date)),
            listOf(MeterReplacement("lifecycle-boundary", date, ORIGINAL_INSTALLATION_ID, "lifecycle-new", final.id, "lifecycle-baseline")),
        )
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val snapshots = MutableStateFlow(CommittedSnapshot(1, initial))
        val repository = object : ReadingRepository {
            override fun observeReadings() = snapshots
            override suspend fun save(intent: SaveReadingIntent): SaveReadingResult = error("Unexpected save")
            override suspend fun delete(id: String): DeleteReadingResult = error("Unexpected deletion")
            override suspend fun replaceMeter(intent: ReplaceMeterIntent): ReplaceMeterResult {
                calls.incrementAndGet()
                assertEquals(date, intent.target.date)
                assertEquals(final, intent.target.finalReading)
                entered.complete(Unit)
                gate.await()
                val snapshot = CommittedSnapshot(2, committed)
                snapshots.value = snapshot
                return ReplaceMeterResult.Replaced(snapshot)
            }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var id = ""
        var taskId = 0
        lateinit var tracker: TrackerViewModel
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
            withContext(Dispatchers.Main) {
                withTimeout(5_000) { snapshotFlow { tracker.state.loadState }.first { it == ReadingLoadState.READY } }
                tracker.selectTab(TrackerTab.HISTORY)
                tracker.selectPeriod(PeriodType.MONTH)
                tracker.openReplacement()
                tracker.replaceMeter()
            }
            withTimeout(5_000) { entered.await() }
            val pending = withContext(Dispatchers.Main) { tracker.state }
            assertTrue(pending.dialog.pending)
            scenario.recreate()
            scenario.onActivity {
                val retained = ViewModelProvider(
                    application.trackerStoreOwner.obtain(id, taskId, setOf(taskId)),
                    TrackerViewModel.factory(repository),
                )[TrackerViewModel::class.java]
                assertSame(tracker, retained)
                assertEquals(pending, retained.state)
                retained.dismissDialog()
                retained.openAdd()
                retained.replaceMeter()
                assertEquals(pending.dialog, retained.state.dialog)
            }
            assertEquals(1, calls.get())
            gate.complete(Unit)
            withContext(Dispatchers.Main) {
                withTimeout(5_000) { snapshotFlow { tracker.state.dialog }.first { it == ReadingDialog.Closed } }
                assertEquals(committed, tracker.state.tracker)
                assertEquals(pending.period, tracker.state.period)
                assertEquals(pending.tab, tracker.state.tab)
            }
            scenario.recreate()
            scenario.onActivity { assertEquals(ReadingDialog.Closed, tracker.state.dialog) }
            scenario.close()
            withContext(Dispatchers.Main) {
                val freshOwner = TrackerStoreOwner()
                try {
                    val fresh = ViewModelProvider(
                        freshOwner.obtain(id, taskId, emptySet()), TrackerViewModel.factory(repository),
                    )[TrackerViewModel::class.java]
                    assertNotEquals(pending.sessionId, fresh.state.sessionId)
                    assertEquals(ReadingDialog.Closed, fresh.state.dialog)
                    withTimeout(5_000) { snapshotFlow { fresh.state.loadState }.first { it == ReadingLoadState.READY } }
                    assertEquals(committed, fresh.state.tracker)
                    assertEquals(TrackerTab.DASHBOARD, fresh.state.tab)
                    assertEquals(1, calls.get())
                } finally { freshOwner.release(id) }
            }
        } finally {
            gate.complete(Unit)
            scenario.close()
        }
    }

    @Test fun activityRecreationRetainsSessionAndFinishingReleasesItWhileFreshOwnerReloadsRecords() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<TrackGasApplication>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var original: TrackerViewModel
        lateinit var id: String
        var taskId = 0
        fun capture(activity: MainActivity): TrackerViewModel {
            val saved = Bundle()
            InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(activity, saved)
            id = saved.getString("tracker_id")!!
            taskId = activity.taskId
            return ViewModelProvider(
                application.trackerStoreOwner.obtain(id, taskId, setOf(taskId)),
                TrackerViewModel.factory(application.readingRepository),
            )[TrackerViewModel::class.java]
        }
        try {
            scenario.onActivity { original = capture(it) }
            withContext(Dispatchers.Main) {
                withTimeout(5_000) { snapshotFlow { original.state.loadState }.first { it == ReadingLoadState.READY } }
                original.selectTab(TrackerTab.HISTORY)
                original.selectPeriod(PeriodType.YEAR)
                original.navigate(-1)
                original.openAdd()
            }
            val before = original.state
            scenario.recreate()
            scenario.onActivity {
                val recreated = capture(it)
                assertSame(original, recreated)
                assertEquals(before, recreated.state)
            }
            scenario.close()
            withContext(Dispatchers.Main) {
                val replacementStore = application.trackerStoreOwner.obtain(id, taskId, setOf(taskId))
                val replacement = ViewModelProvider(replacementStore, TrackerViewModel.factory(application.readingRepository))[TrackerViewModel::class.java]
                assertNotSame(original, replacement)
                assertNotEquals(before.sessionId, replacement.state.sessionId)
                assertEquals(ReadingDialog.Closed, replacement.state.dialog)
                assertEquals(TrackerTab.DASHBOARD, replacement.state.tab)
                assertEquals(CalendarPeriod.containing(PeriodType.WEEK, replacement.today()), replacement.state.period)
                withTimeout(5_000) { snapshotFlow { replacement.state.loadState }.first { it == ReadingLoadState.READY } }
                assertEquals(before.readings, replacement.state.readings)
                application.trackerStoreOwner.release(id)
                val freshOwner = TrackerStoreOwner()
                val fresh = ViewModelProvider(freshOwner.obtain(id, taskId, emptySet()), TrackerViewModel.factory(application.readingRepository))[TrackerViewModel::class.java]
                assertNotEquals(before.sessionId, fresh.state.sessionId)
                assertEquals(ReadingDialog.Closed, fresh.state.dialog)
                withTimeout(5_000) { snapshotFlow { fresh.state.loadState }.first { it == ReadingLoadState.READY } }
                assertEquals(before.readings, fresh.state.readings)
                freshOwner.release(id)
            }
        } finally { scenario.close() }
    }
}
