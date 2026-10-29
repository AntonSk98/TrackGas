package com.trackgas.app.tracking

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackgas.app.R
import com.trackgas.app.data.ReadingDatabase
import com.trackgas.app.data.RoomReadingRepository
import java.time.LocalDate
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MeterReplacementOfflineUiTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "replacement-ui-${UUID.randomUUID()}.db"
    private var database: ReadingDatabase? = null
    private var store = ViewModelStore()
    private var model by mutableStateOf<TrackerViewModel?>(null)
    private var today = LocalDate.of(2026, 10, 27)
    private val tracker get() = checkNotNull(model)
    private fun text(id: Int) = context.getString(id)

    private fun openDatabaseAndSession() {
        val opened = Room.databaseBuilder(context, ReadingDatabase::class.java, databaseName)
            .addMigrations(ReadingDatabase.MIGRATION_1_2)
            .addCallback(ReadingDatabase.INITIALIZE)
            .build()
        database = opened
        rule.runOnIdle {
            model = TrackerViewModel(RoomReadingRepository(opened)) { today }.also { store.put("tracker", it) }
        }
    }

    private fun waitReady() = rule.waitUntil(10_000) { tracker.state.loadState == ReadingLoadState.READY }
    private fun waitClosed() = rule.waitUntil(10_000) { tracker.state.dialog == ReadingDialog.Closed }
    private fun add(value: String) {
        rule.onNodeWithContentDescription(text(R.string.add_reading)).performClick()
        rule.onNode(hasSetTextAction() and hasText(text(R.string.value_label))).performTextInput(value)
        rule.onNodeWithText(text(R.string.save)).performClick()
        waitClosed()
    }

    private fun assertDailyValuesAndChart(nextDayValue: String, total: String) {
        rule.onNodeWithText(text(R.string.dashboard_tab)).performClick()
        rule.onNodeWithText("$total m³").performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("28") and hasText("5 m³")).performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("29") and hasText("$nextDayValue m³")).performScrollTo().assertIsDisplayed()
        rule.runOnIdle {
            val projection = chartProjection(tracker.state.period, tracker.state.dashboard)
            assertEquals(listOf(27, 28), projection.entries.map { it.offset })
            assertEquals(listOf("5", nextDayValue), projection.entries.map { it.cubicMetres.stripTrailingZeros().toPlainString() })
            assertEquals(total, tracker.state.dashboard.total.cubicMetres!!.stripTrailingZeros().toPlainString())
        }
    }

    private fun assertHistory(nextDayValue: String) {
        rule.onNodeWithText(text(R.string.history_tab)).performClick()
        for (value in listOf("$nextDayValue m³", "0 m³", "1105 m³", "1100 m³")) {
            rule.onNode(hasText(value) and hasClickAction()).performScrollTo().assertIsDisplayed()
        }
        val newInitial = context.getString(R.string.meter_role, context.getString(R.string.meter_number, 2), text(R.string.initial_reading))
        val oldFinal = context.getString(R.string.meter_role, context.getString(R.string.meter_number, 1), text(R.string.final_reading))
        rule.onNode(hasText(newInitial) and hasClickAction()).performScrollTo().assertIsDisplayed()
        rule.onNode(hasText(oldFinal) and hasClickAction()).performScrollTo().assertIsDisplayed()
    }

    @After fun cleanup() {
        rule.runOnIdle { store.clear() }
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun exactOctoberScenarioCommitsThroughUiAndSurvivesOfflineColdReopen() {
        replacementAndColdReopen(nextDayValue = "3", total = "8")
    }

    @Test fun noValueReplacementThenFirstNextDayReadingFiveUsesAutomaticZeroAfterColdReopen() {
        replacementAndColdReopen(nextDayValue = "5", total = "10")
    }

    private fun replacementAndColdReopen(nextDayValue: String, total: String) {
        openDatabaseAndSession()
        rule.setContent {
            MaterialTheme {
                val current = model
                if (current != null) TrackerScreen(
                    state = current.state, currentDate = current::today,
                    onSelectTab = current::selectTab, onSelectPeriod = current::selectPeriod,
                    onNavigate = current::navigate, onRetryLoad = current::retryLoad,
                    onOpenAdd = current::openAdd, onOpenEdit = current::openEdit,
                    onOpenDelete = current::openDelete, onDismissDialog = current::dismissDialog,
                    onClearDialogError = current::clearDialogError, onSave = current::save,
                    onDelete = current::delete, onOpenReplacement = current::openReplacement,
                    onReplaceMeter = current::replaceMeter,
                    onOpenPurge = current::openPurge, onPurge = current::purge,
                )
            }
        }
        waitReady()
        rule.onNodeWithText(text(R.string.month)).performClick()
        val selected = tracker.state.period
        add("1100")
        rule.runOnIdle { today = today.plusDays(1) }
        add("1105")
        rule.onNodeWithContentDescription(text(R.string.tracker_options)).performClick()
        rule.onNodeWithText(text(R.string.replace_meter)).performClick()
        rule.onNodeWithText("1105 m³", substring = true).performScrollTo().assertIsDisplayed()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        rule.onNodeWithText(text(R.string.replacement_explanation)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.confirm_replacement)).performScrollTo().performClick()
        waitClosed()
        rule.runOnIdle {
            val boundary = tracker.state.tracker.replacements.single()
            val baseline = tracker.state.readings.single { it.id == boundary.initialReadingId }
            assertEquals("0".toBigDecimal(), baseline.cubicMetres)
        }
        rule.runOnIdle { today = today.plusDays(1) }
        add(nextDayValue)
        rule.runOnIdle { assertEquals(selected, tracker.state.period) }
        assertDailyValuesAndChart(nextDayValue, total)
        assertHistory(nextDayValue)
        val committed = tracker.state.tracker
        val currentInstallation = committed.currentInstallation.id
        rule.runOnIdle {
            assertEquals(selected, tracker.state.period)
            assertEquals(2, committed.installations.size)
            assertEquals(1, committed.replacements.size)
            store.clear()
            model = null
        }
        database!!.close()
        store = ViewModelStore()
        openDatabaseAndSession()
        waitReady()
        rule.runOnIdle {
            assertEquals(TrackerTab.DASHBOARD, tracker.state.tab)
            assertEquals(CalendarPeriod.containing(PeriodType.WEEK, today), tracker.state.period)
            assertEquals(committed, tracker.state.tracker)
            assertEquals(currentInstallation, tracker.state.tracker.currentInstallation.id)
            assertEquals(ReadingDialog.Closed, tracker.state.dialog)
        }
        rule.onNodeWithText(text(R.string.month)).performClick()
        rule.runOnIdle { assertEquals(selected, tracker.state.period) }
        assertDailyValuesAndChart(nextDayValue, total)
        assertHistory(nextDayValue)
    }
}
