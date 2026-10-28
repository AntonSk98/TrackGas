package com.trackgas.app

import android.os.Bundle
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.trackgas.app.tracking.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class TrackerLifecycleTest {
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
