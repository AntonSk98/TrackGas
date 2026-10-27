package com.trackgas.app.tracking

import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trackgas.app.R
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TrackerScreenPersistenceUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 27)
    private val period = CalendarPeriod.containing(PeriodType.WEEK, today)
    private val reading = Reading("reading-1", today.minusDays(1), "123.45".toBigDecimal())
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var state by mutableStateOf(snapshot())
    private var saveCalls = 0
    private var deleteCalls = 0
    private var retryCalls = 0
    private var consumed = 0
    private var lastValue = ""

    private fun snapshot(
        readings: List<Reading> = emptyList(),
        tab: TrackerTab = TrackerTab.DASHBOARD,
        load: ReadingLoadState = ReadingLoadState.READY,
    ) = TrackerState(
        readings = readings,
        period = period,
        dashboard = dashboard(readings, period),
        tab = tab,
        loadState = load,
    )

    private fun text(id: Int) = context.getString(id)

    private fun showScreen(): StateRestorationTester {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            MaterialTheme {
                TrackerScreen(
                    state = state,
                    currentDate = { today },
                    onSelectTab = { state = state.copy(tab = it) },
                    onSelectPeriod = {},
                    onNavigate = {},
                    onRetryLoad = {
                        retryCalls++
                        state = state.copy(loadState = ReadingLoadState.LOADING)
                    },
                    onConsumeOperation = {
                        consumed++
                        state = state.copy(operation = ReadingOperation.Idle)
                    },
                    onSave = { _, _, value, _ ->
                        saveCalls++
                        lastValue = value
                        state = state.copy(operation = ReadingOperation.Pending)
                    },
                    onDelete = {
                        deleteCalls++
                        state = state.copy(operation = ReadingOperation.Pending)
                    },
                )
            }
        }
        return restoration
    }

    private fun operation(value: ReadingOperation) {
        rule.runOnIdle { state = state.copy(operation = value) }
    }

    private fun openAdd() {
        rule.onNodeWithContentDescription(text(R.string.add_reading)).performClick()
    }

    private fun enterValue(value: String) {
        rule.onNode(hasSetTextAction()).performTextInput(value)
    }

    private fun assertDraft(value: String) {
        rule.onNode(hasText(value, substring = true)).assertTextContains(value)
    }

    private fun openDelete() {
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick) and hasText("m³", substring = true))
            .performTouchInput { longClick() }
        rule.onNodeWithText(text(R.string.delete_reading)).assertIsDisplayed()
    }

    @Test
    fun loadingAndFailureDoNotShowEmptyTrackerAndRetryLoads() {
        state = snapshot(load = ReadingLoadState.LOADING)
        showScreen()
        rule.onNodeWithText(text(R.string.loading_readings)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.no_consumption)).assertDoesNotExist()
        rule.onNodeWithContentDescription(text(R.string.add_reading)).assertIsNotEnabled()

        rule.runOnIdle { state = state.copy(loadState = ReadingLoadState.ERROR) }
        rule.onNodeWithText(text(R.string.load_storage_error)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.no_readings)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.retry)).performClick()
        rule.onNodeWithText(text(R.string.loading_readings)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(1, retryCalls) }

        rule.runOnIdle { state = state.copy(loadState = ReadingLoadState.READY) }
        rule.onNodeWithContentDescription(text(R.string.add_reading)).assertIsEnabled()
        rule.onNodeWithText(text(R.string.no_consumption)).assertIsDisplayed()
    }

    @Test
    fun invalidSaveSurvivesRestorationAndClearsWhenInputChanges() {
        val restoration = showScreen()
        openAdd()
        enterValue("12")
        rule.onNodeWithText(text(R.string.save)).performClick()
        operation(ReadingOperation.Invalid(ReadingErrors(value = ReadingError.BELOW_PREVIOUS)))
        rule.onNodeWithText(text(R.string.below_previous)).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        assertDraft("12")
        rule.onNodeWithText(text(R.string.below_previous)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, consumed) }

        rule.onNode(hasSetTextAction()).performTextReplacement("123")
        rule.onNodeWithText(text(R.string.below_previous)).assertDoesNotExist()
        assertDraft("123")
        rule.runOnIdle { assertEquals(1, consumed) }
    }

    @Test
    fun duplicateSaveOpensPickerWithoutDiscardingDraft() {
        val duplicate = reading.copy(date = today)
        state = snapshot(readings = listOf(duplicate))
        showScreen()
        openAdd()
        enterValue("456")
        rule.onNodeWithText(text(R.string.save)).performClick()
        operation(ReadingOperation.Invalid(ReadingErrors(date = ReadingError.DUPLICATE_DATE)))
        rule.onNodeWithText(text(R.string.duplicate_date)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.confirm_date)).assertIsNotEnabled()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertDraft("456")
        rule.onNodeWithText(text(R.string.duplicate_date)).assertDoesNotExist()
    }

    @Test
    fun failedSaveCanRetryAndPendingAndSuccessSurviveRestoration() {
        val restoration = showScreen()
        openAdd()
        enterValue("123.45")
        rule.onNodeWithText(text(R.string.save)).performClick()
        rule.onNodeWithText(text(R.string.saving_reading)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.save)).assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.cancel)).assertIsNotEnabled()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertDraft("123.45")
        restoration.emulateSavedInstanceStateRestore()
        assertDraft("123.45")
        rule.onNodeWithText(text(R.string.save)).assertIsNotEnabled()
        rule.runOnIdle { assertEquals(1, saveCalls) }

        operation(ReadingOperation.Failed)
        rule.onNodeWithText(text(R.string.save_storage_error)).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        assertDraft("123.45")
        rule.onNodeWithText(text(R.string.save_storage_error)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, consumed) }
        rule.onNodeWithText(text(R.string.save)).performClick()
        rule.runOnIdle {
            assertEquals(2, saveCalls)
            assertEquals("123.45", lastValue)
        }
        operation(ReadingOperation.Saved)
        rule.onNodeWithText(text(R.string.save)).assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText(text(R.string.save)).assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, consumed) }
        openAdd()
        rule.onNodeWithText(text(R.string.save)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(2, consumed) }
    }

    @Test
    fun failedDeleteCanRetryAndConfirmationSurvivesRestoration() {
        state = snapshot(readings = listOf(reading), tab = TrackerTab.HISTORY)
        val restoration = showScreen()
        openDelete()
        rule.onNodeWithText(text(R.string.delete)).performClick()
        rule.onNodeWithText(text(R.string.deleting_reading)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.delete)).assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.cancel)).assertIsNotEnabled()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.onNodeWithText(text(R.string.delete_reading)).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText(text(R.string.delete_reading)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.delete)).assertIsNotEnabled()
        rule.runOnIdle { assertEquals(1, deleteCalls) }

        operation(ReadingOperation.Failed)
        rule.onNodeWithText(text(R.string.delete_storage_error)).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText(text(R.string.delete_reading)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.delete_storage_error)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, consumed) }
        rule.onNodeWithText(text(R.string.delete)).performClick()
        rule.runOnIdle { assertEquals(2, deleteCalls) }
        operation(ReadingOperation.Deleted)
        rule.onNodeWithText(text(R.string.delete_reading)).assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText(text(R.string.delete_reading)).assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, consumed) }
    }

    @Test
    fun restoredPendingDraftIsDiscardedWhenViewModelSessionChanges() {
        val restoration = showScreen()
        openAdd()
        enterValue("123.45")
        rule.onNodeWithText(text(R.string.save)).performClick()
        restoration.emulateSavedInstanceStateRestore()
        assertDraft("123.45")

        rule.runOnIdle {
            state = state.copy(sessionId = "new-view-model", operation = ReadingOperation.Idle)
        }
        rule.onNodeWithText(text(R.string.save)).assertDoesNotExist()
        openAdd()
        rule.onNodeWithText(text(R.string.save)).assertIsDisplayed()
        rule.onNodeWithText("123.45").assertDoesNotExist()
    }

    @Test
    fun observationFailureCanBeRetriedWithoutDiscardingDraft() {
        showScreen()
        openAdd()
        enterValue("123.45")
        rule.runOnIdle { state = state.copy(loadState = ReadingLoadState.ERROR) }
        assertDraft("123.45")
        rule.onAllNodesWithText(text(R.string.retry))[0].performClick()
        rule.runOnIdle { assertEquals(1, retryCalls) }
        assertDraft("123.45")
        rule.onNodeWithText(text(R.string.save)).assertIsNotEnabled()
        rule.runOnIdle { state = state.copy(loadState = ReadingLoadState.READY) }
        assertDraft("123.45")
        rule.onNodeWithText(text(R.string.save)).assertIsEnabled()
    }

    @Test
    fun observationFailureCanBeRetriedFromDeleteConfirmation() {
        state = snapshot(readings = listOf(reading), tab = TrackerTab.HISTORY)
        showScreen()
        openDelete()
        rule.runOnIdle { state = state.copy(loadState = ReadingLoadState.ERROR) }
        rule.onAllNodesWithText(text(R.string.retry))[0].performClick()
        rule.runOnIdle { assertEquals(1, retryCalls) }
        rule.onNodeWithText(text(R.string.delete_reading)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.delete)).assertIsNotEnabled()
        rule.runOnIdle { state = state.copy(loadState = ReadingLoadState.READY) }
        rule.onNodeWithText(text(R.string.delete)).assertIsEnabled()
    }
}
