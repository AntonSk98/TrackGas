package com.trackgas.app.tracking

import android.content.Context
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.trackgas.app.MainActivity
import com.trackgas.app.R
import com.trackgas.app.TrackGasApplication
import com.trackgas.app.data.ReadingDatabase
import com.trackgas.app.data.RoomReadingRepository
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PurgeActivityUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "purge-activity-${UUID.randomUUID()}.db"
    private fun text(id: Int) = context.getString(id)

    @Test fun realActivityPurgesReplacementHistoryAndReopensForNewBaseline() {
        fun open() = Room.databaseBuilder(context, ReadingDatabase::class.java, name)
            .addMigrations(ReadingDatabase.MIGRATION_1_2).addCallback(ReadingDatabase.INITIALIZE).build()
        var database = open()
        val today = LocalDate.now()
        var repository = RoomReadingRepository(database)
        runBlocking {
            repository.save(SaveReadingIntent.Add(today.minusDays(1), parseMeter("100", Locale.US), today))
            repository.save(SaveReadingIntent.Add(today, parseMeter("110", Locale.US), today))
        }
        val app = context as TrackGasApplication
        var id = ""
        var taskId = 0
        lateinit var tracker: TrackerViewModel
        fun installTracker() {
            rule.runOnIdle {
                val activity = rule.activity
                val saved = Bundle()
                InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(activity, saved)
                id = saved.getString("tracker_id")!!
                taskId = activity.taskId
                val store = app.trackerStoreOwner.obtain(id, taskId, setOf(taskId))
                store.clear()
                tracker = ViewModelProvider(store, TrackerViewModel.factory(repository))[TrackerViewModel::class.java]
            }
            rule.activityRule.scenario.recreate()
            rule.waitUntil(10_000) { tracker.state.loadState == ReadingLoadState.READY }
        }
        fun openPurge() {
            rule.onNodeWithContentDescription(text(R.string.tracker_options)).performClick()
            rule.onNodeWithText(text(R.string.purge_tracking_data)).performClick()
        }
        try {
            installTracker()
            rule.onNodeWithText(text(R.string.month)).performClick()
            rule.onNodeWithText(text(R.string.history_tab)).performClick()
            val period = tracker.state.period
            rule.onNodeWithContentDescription(text(R.string.tracker_options)).performClick()
            rule.onNodeWithText(text(R.string.replace_meter)).performClick()
            rule.onNodeWithText(text(R.string.confirm_replacement)).performScrollTo().performClick()
            rule.waitUntil(10_000) { tracker.state.dialog == ReadingDialog.Closed }
            val original = tracker.state.tracker
            assertEquals(1, original.replacements.size)
            openPurge()
            rule.onNodeWithText(text(R.string.cancel)).performClick()
            assertEquals(original, tracker.state.tracker)
            openPurge()
            rule.onNodeWithText(text(R.string.purge_all_data)).performClick()
            rule.waitUntil(10_000) { tracker.state.dialog == ReadingDialog.Closed && tracker.state.readings.isEmpty() }
            val purged = tracker.state.tracker
            assertTrue(original.installations.none { it.id == purged.currentInstallation.id })
            rule.onNodeWithText(text(R.string.no_readings)).performScrollTo().assertIsDisplayed()
            assertEquals(TrackerTab.HISTORY, tracker.state.tab)
            assertEquals(period, tracker.state.period)
            rule.onNodeWithText(text(R.string.dashboard_tab)).performClick()
            rule.onNodeWithText(text(R.string.no_consumption)).performScrollTo().assertIsDisplayed()
            rule.activityRule.scenario.recreate()
            rule.waitUntil(10_000) { tracker.state.tracker == purged }

            rule.runOnIdle { app.trackerStoreOwner.obtain(id, taskId, setOf(taskId)).clear() }
            database.close()
            database = open()
            repository = RoomReadingRepository(database)
            installTracker()
            assertEquals(purged, tracker.state.tracker)
            rule.onNodeWithContentDescription(text(R.string.add_reading)).performClick()
            rule.onNode(hasSetTextAction() and hasText(text(R.string.value_label))).performTextInput("1")
            rule.onNodeWithText(text(R.string.save)).performClick()
            rule.waitUntil(10_000) { tracker.state.dialog == ReadingDialog.Closed && tracker.state.readings.size == 1 }
            assertEquals(purged.currentInstallation.id, tracker.state.readings.single().installationId)
            assertNull(tracker.state.dashboard.total.cubicMetres)
        } finally {
            rule.runOnIdle { if (id.isNotEmpty()) app.trackerStoreOwner.release(id) }
            database.close()
            context.deleteDatabase(name)
        }
    }
}
