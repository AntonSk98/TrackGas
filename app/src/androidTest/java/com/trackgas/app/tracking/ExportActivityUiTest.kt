package com.trackgas.app.tracking

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trackgas.app.MainActivity
import com.trackgas.app.R
import com.trackgas.app.TrackGasApplication
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportActivityUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private fun text(id: Int) = context.getString(id)

    @After fun stopObserving() = scope.cancel()

    private fun tracker(repository: ReadingRepository? = null): TrackerViewModel {
        lateinit var tracker: TrackerViewModel
        rule.runOnIdle {
            val activity = rule.activity
            val saved = Bundle()
            instrumentation.callActivityOnSaveInstanceState(activity, saved)
            val app = activity.application as TrackGasApplication
            val store = app.trackerStoreOwner.obtain(saved.getString("tracker_id")!!, activity.taskId, setOf(activity.taskId))
            if (repository != null) store.clear()
            tracker = ViewModelProvider(store, TrackerViewModel.factory(repository ?: app.readingRepository))[TrackerViewModel::class.java]
        }
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10_000) { tracker.state.loadState == ReadingLoadState.READY }
        return tracker
    }

    private fun monitor(result: Instrumentation.ActivityResult): Instrumentation.ActivityMonitor {
        val filter = IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addDataType("text/csv")
        }
        return Instrumentation.ActivityMonitor(filter, result, true).also(instrumentation::addMonitor)
    }

    private fun startExport() {
        rule.onNodeWithContentDescription(text(R.string.tracker_options)).performClick()
        rule.onNodeWithText(text(R.string.export_csv)).assertIsEnabled().performClick()
    }

    private fun dismissOutside() {
        val time = SystemClock.uptimeMillis()
        val y = context.resources.displayMetrics.heightPixels * .1f
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 10f, y, 0)
        val up = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, 10f, y, 0)
        try {
            instrumentation.sendPointerSync(down)
            instrumentation.sendPointerSync(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    @Test fun pickerCancellationAndFailureKeepTrackerAndAllowRetry() {
        val tracker = tracker()
        rule.onNodeWithText(text(R.string.month)).performClick()
        rule.onNodeWithContentDescription(text(R.string.previous_period)).performClick()
        rule.onNodeWithText(text(R.string.history_tab)).performClick()
        rule.waitForIdle()
        val before = tracker.state
        assertEquals(TrackerTab.HISTORY, before.tab)
        val states = CopyOnWriteArrayList<ExportState>()
        rule.runOnIdle {
            scope.launch { snapshotFlow { tracker.state.export }.collect { states.add(it) } }
        }

        val cancel = monitor(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        try {
            startExport()
            rule.waitUntil(10_000) { cancel.hits == 1 && tracker.state.export is ExportState.Idle }
        } finally {
            instrumentation.removeMonitor(cancel)
        }
        rule.runOnIdle {
            assertEquals(before.tracker, tracker.state.tracker)
            assertEquals(before.period, tracker.state.period)
            assertEquals(before.tab, tracker.state.tab)
            assertTrue(states.none { it is ExportState.Complete })
        }

        val failure = monitor(
            Instrumentation.ActivityResult(
                Activity.RESULT_OK,
                Intent().setData(Uri.parse("content://com.trackgas.invalid-export-test/file")),
            ),
        )
        try {
            startExport()
            rule.waitUntil(10_000) {
                states.any { it is ExportState.Complete && !it.succeeded }
            }
            rule.onNodeWithText(text(R.string.export_failure)).assertExists()
            rule.onNodeWithText(text(R.string.ok)).performClick()
            rule.waitUntil(10_000) { tracker.state.export is ExportState.Idle }
            assertEquals(1, failure.hits)
        } finally {
            instrumentation.removeMonitor(failure)
        }
        rule.runOnIdle {
            assertEquals(before.tracker, tracker.state.tracker)
            assertEquals(before.period, tracker.state.period)
            assertEquals(before.tab, tracker.state.tab)
        }

        val retry = monitor(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        try {
            startExport()
            rule.waitUntil(10_000) { retry.hits == 1 && tracker.state.export is ExportState.Idle }
            assertTrue(retry.hits == 1)
        } finally {
            instrumentation.removeMonitor(retry)
        }
    }

    @Test fun successfulDestinationWritesSnapshotAndLeavesNavigationUnchanged() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val date = LocalDate.of(2026, 10, 28)
        val first = "11111111-1111-4111-8111-111111111111"
        val second = "22222222-2222-4222-8222-222222222222"
        val data = TrackerData(
            readings = listOf(
                Reading("later", date.plusDays(1), "3.250".toBigDecimal(), second),
                Reading("initial", date, "0".toBigDecimal(), second),
                Reading("final", date, "1105.000".toBigDecimal(), first),
                Reading("earlier", date.minusDays(1), "1099.500".toBigDecimal(), first),
            ),
            installations = listOf(MeterInstallation(first), MeterInstallation(second, date)),
            replacements = listOf(MeterReplacement("replacement", date, first, second, "final", "initial")),
        )
        val repository = object : ReadingRepository {
            override fun observeReadings() = MutableStateFlow(CommittedSnapshot(1, data))
            override suspend fun save(intent: SaveReadingIntent): SaveReadingResult = error("Unexpected save")
            override suspend fun delete(id: String): DeleteReadingResult = error("Unexpected delete")
            override suspend fun replaceMeter(intent: ReplaceMeterIntent): ReplaceMeterResult = error("Unexpected replacement")
            override suspend fun purge(): CommittedSnapshot = error("Unexpected purge")
        }
        val tracker = tracker(repository)
        val before = tracker.state
        val states = CopyOnWriteArrayList<ExportState>()
        rule.runOnIdle {
            scope.launch { snapshotFlow { tracker.state.export }.collect { states.add(it) } }
        }
        val resolver = context.contentResolver
        val destination = requireNotNull(resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "trackgas-test-${UUID.randomUUID()}.csv")
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            },
        ))
        val picker = monitor(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(destination)))
        try {
            startExport()
            rule.waitUntil(10_000) {
                states.any { it is ExportState.Complete && it.succeeded }
            }
            rule.onNodeWithText(text(R.string.export_success)).assertDoesNotExist()
            rule.onNodeWithTag("export-success-icon").assertIsDisplayed()
                .assertContentDescriptionEquals(text(R.string.export_success))
            rule.onNodeWithText(text(R.string.ok)).assertDoesNotExist()
            dismissOutside()
            rule.waitUntil(10_000) { tracker.state.export is ExportState.Idle }
            assertEquals(1, picker.hits)
            assertArrayEquals(
                ("meter_id;date;meter_value\n" +
                    "$first;2026-10-27;1099.500\n" +
                    "$first;2026-10-28;1105.000\n" +
                    "$second;2026-10-28;0\n" +
                    "$second;2026-10-29;3.250\n").toByteArray(Charsets.UTF_8),
                resolver.openInputStream(destination)!!.use { it.readBytes() },
            )
            rule.runOnIdle {
                assertEquals(before.tracker, tracker.state.tracker)
                assertEquals(before.period, tracker.state.period)
                assertEquals(before.tab, tracker.state.tab)
            }
        } finally {
            instrumentation.removeMonitor(picker)
            resolver.delete(destination, null, null)
        }
    }
}
