package com.trackgas.app.tracking

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trackgas.app.MainActivity
import com.trackgas.app.R
import com.trackgas.app.TrackGasApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CsvImportActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun text(id: Int) = context.getString(id)

    private fun monitor(result: Instrumentation.ActivityResult): Instrumentation.ActivityMonitor {
        val filter = IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addDataType("*/*")
        }
        return Instrumentation.ActivityMonitor(filter, result, true).also(instrumentation::addMonitor)
    }

    private fun tracker(): TrackerViewModel {
        lateinit var tracker: TrackerViewModel
        rule.runOnIdle {
            val activity = rule.activity
            val saved = android.os.Bundle()
            instrumentation.callActivityOnSaveInstanceState(activity, saved)
            val app = activity.application as TrackGasApplication
            val store = app.trackerStoreOwner.obtain(saved.getString("tracker_id")!!, activity.taskId, setOf(activity.taskId))
            tracker = ViewModelProvider(store, TrackerViewModel.factory(app.readingRepository))[TrackerViewModel::class.java]
        }
        rule.waitUntil(10_000) { tracker.state.loadState == ReadingLoadState.READY }
        return tracker
    }

    @Test fun pickerCancellationDoesNothingAndUnreadableSelectionShowsSafeError() {
        val tracker = tracker()
        val before = tracker.state.tracker
        val cancel = monitor(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        try {
            rule.runOnIdle { rule.activity.startImport() }
            rule.waitUntil(10_000) { cancel.hits == 1 && tracker.state.csvImport is CsvImportState.Idle }
        } finally {
            instrumentation.removeMonitor(cancel)
        }
        rule.runOnIdle {
            assertEquals(before, tracker.state.tracker)
            assertEquals(CsvImportState.Idle, tracker.state.csvImport)
        }

        val unreadable = monitor(Instrumentation.ActivityResult(
            Activity.RESULT_OK,
            Intent().setData(Uri.parse("content://com.trackgas.invalid-import-test/file")),
        ))
        try {
            rule.runOnIdle { rule.activity.startImport() }
            rule.waitUntil(10_000) {
                unreadable.hits == 1 && tracker.state.csvImport is CsvImportState.Failed
            }
            rule.onNodeWithText(text(R.string.csv_import_error_file)).assertExists()
            rule.onNodeWithText(text(R.string.ok)).performClick()
            rule.waitUntil(10_000) { tracker.state.csvImport is CsvImportState.Idle }
        } finally {
            instrumentation.removeMonitor(unreadable)
        }
        rule.runOnIdle {
            assertEquals(before, tracker.state.tracker)
            assertTrue(tracker.state.csvImport is CsvImportState.Idle)
        }
    }

    @Test fun inFlightReadSurvivesActivityRecreationWithoutReadingTwice() {
        val viewModel = tracker()
        lateinit var request: String
        rule.runOnIdle { request = requireNotNull(viewModel.requestCsvImport()) }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val opens = AtomicInteger()
        val bytes = "meter_id;date;meter_value\n".toByteArray(Charsets.UTF_8)
        rule.runOnIdle {
            viewModel.readCsvImport(request) {
                opens.incrementAndGet()
                object : InputStream() {
                    private val source = ByteArrayInputStream(bytes)
                    private var waited = false
                    override fun read(): Int {
                        if (!waited) {
                            waited = true
                            entered.countDown()
                            check(release.await(10, TimeUnit.SECONDS))
                        }
                        return source.read()
                    }
                }
            }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        rule.activityRule.scenario.recreate()
        assertSame(viewModel, tracker())
        rule.runOnIdle {
            assertEquals(CsvImportState.Reading(request), viewModel.state.csvImport)
        }
        release.countDown()
        rule.waitUntil(10_000) { viewModel.state.csvImport is CsvImportState.Preview }
        rule.runOnIdle {
            assertEquals(1, opens.get())
            assertEquals(request, (viewModel.state.csvImport as CsvImportState.Preview).requestId)
            viewModel.cancelCsvImport(request)
        }
        rule.waitUntil(5_000) { viewModel.state.csvImport is CsvImportState.Idle }
    }
}
