package com.trackgas.app.tracking

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.MotionEvent
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.trackgas.app.R
import java.io.IOException
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MeterReplacementUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val date = LocalDate.of(2026, 10, 28)
    private var currentDate = date
    private val final = Reading("final", date, "1105.0000".toBigDecimal())
    private val previous = Reading("previous", date.minusDays(1), "1100".toBigDecimal())
    private val repository = UiRepository(TrackerData(listOf(previous, final)))
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var tracker: TrackerViewModel
    private var activeTracker by mutableStateOf<TrackerViewModel?>(null)

    private fun text(id: Int) = context.getString(id)
    private fun newTracker(): TrackerViewModel = TrackerViewModel(repository) { currentDate }.also {
        ViewModelStore().apply { put("tracker", it) }.also(stores::add)
    }

    private fun show(doubleFont: Boolean = false, waitReady: Boolean = true): StateRestorationTester {
        rule.runOnIdle { tracker = newTracker(); activeTracker = tracker }
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                if (doubleFont) { screenWidthDp = 360; fontScale = 2f }
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, if (doubleFont) 2f else 1f),
            ) {
                MaterialTheme {
                    val model = checkNotNull(activeTracker)
                    TrackerScreen(
                        state = model.state,
                        currentDate = model::today,
                        onSelectTab = model::selectTab,
                        onSelectPeriod = model::selectPeriod,
                        onNavigate = model::navigate,
                        onRetryLoad = model::retryLoad,
                        onOpenAdd = model::openAdd,
                        onOpenEdit = model::openEdit,
                        onOpenDelete = model::openDelete,
                        onDismissDialog = model::dismissDialog,
                        onClearDialogError = model::clearDialogError,
                        onSave = model::save,
                        onDelete = model::delete,
                        onOpenReplacement = model::openReplacement,
                        onReplaceMeter = model::replaceMeter,
                    )
                }
            }
        }
        if (waitReady) rule.waitUntil { tracker.state.loadState == ReadingLoadState.READY }
        return restoration
    }

    @After fun cleanup() {
        rule.runOnIdle { stores.forEach { it.clear() } }
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun options() = rule.onNodeWithContentDescription(text(R.string.tracker_options))
    private fun add() = rule.onNodeWithContentDescription(text(R.string.add_reading))
    private fun assertNoReplacementInput() {
        rule.onNode(isDialog()).assertExists()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
    private fun confirm() = rule.onNodeWithText(text(R.string.confirm_replacement))
    private fun openReplacement() {
        options().performClick()
        rule.onNodeWithText(text(R.string.replace_meter)).performClick()
    }
    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }
    private fun outside() {
        rule.onNode(isDialog()).performTouchInput { click(Offset(1f, 1f)) }
    }
    private fun outsideMenu() {
        rule.onNodeWithText(text(R.string.replace_meter)).assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(208.dp)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val time = SystemClock.uptimeMillis()
        val y = context.resources.displayMetrics.heightPixels * .65f
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 40f, y, 0)
        val up = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, 40f, y, 0)
        try {
            instrumentation.sendPointerSync(down)
            instrumentation.sendPointerSync(up)
        } finally {
            down.recycle()
            up.recycle()
        }
        instrumentation.waitForIdleSync()
        rule.waitForIdle()
    }

    @Test fun optionsPrecedesRightmostAddOnBothTabsAndMenuPreservesNavigation() {
        show()
        rule.onNodeWithText(text(R.string.month)).performClick()
        rule.onNodeWithContentDescription(text(R.string.previous_period)).performClick()
        val selected = tracker.state.period
        for (tab in listOf(TrackerTab.DASHBOARD, TrackerTab.HISTORY)) {
            rule.onNodeWithText(text(if (tab == TrackerTab.HISTORY) R.string.history_tab else R.string.dashboard_tab)).performClick()
            options().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            add().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            assertTrue(options().fetchSemanticsNode().boundsInRoot.right <= add().fetchSemanticsNode().boundsInRoot.left)
            options().performClick()
            rule.onAllNodes(hasAnyAncestor(isPopup()) and hasClickAction()).assertCountEquals(1)
            rule.onNodeWithText(text(R.string.replace_meter)).assertIsDisplayed()
            back()
            rule.onNodeWithText(text(R.string.replace_meter)).assertDoesNotExist()
            options().performClick()
            outsideMenu()
            rule.waitUntil(5_000) { rule.onAllNodes(isPopup()).fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithText(text(R.string.replace_meter)).assertDoesNotExist()
            rule.runOnIdle {
                assertEquals(tab, tracker.state.tab)
                assertEquals(selected, tracker.state.period)
                assertEquals(listOf(previous, final), repository.data.readings)
            }
        }
    }

    @Test fun automaticZeroNeedsNoValueAndRepeatedSameDayIsUnavailable() {
        show()
        openReplacement()
        assertNoReplacementInput()
        rule.onNodeWithText("1105.0000", substring = true).assertIsDisplayed()
        rule.onNode(hasText(text(R.string.date)) and hasText(dateText(date, Locale.getDefault())))
            .assertIsDisplayed().assert(hasSetTextAction().not())
        rule.onNodeWithContentDescription(context.getString(R.string.choose_date, dateText(date, Locale.getDefault()))).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.replacement_explanation)).performScrollTo().assertIsDisplayed()
        confirm().performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.confirm_replacement)).assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(1, repository.replaceCalls)
            assertEquals(1, repository.data.replacements.size)
            assertEquals("0".toBigDecimal(), repository.data.readings.single { it.id == "initial-1" }.cubicMetres)
        }
        openReplacement()
        rule.onNodeWithText(text(R.string.replacement_already_replaced)).assertIsDisplayed()
        confirm().assertDoesNotExist()
        assertNoReplacementInput()
    }

    @Test fun cancelBackAndOutsideCloseConfirmationButKeepSavedFinal() {
        show()
        repeat(3) { attempt ->
            openReplacement()
            assertNoReplacementInput()
            when (attempt) {
                0 -> rule.onNodeWithText(text(R.string.cancel)).performScrollTo().performClick()
                1 -> back()
                else -> outside()
            }
            rule.onNode(isDialog()).assertDoesNotExist()
        }
        openReplacement()
        assertNoReplacementInput()
        rule.runOnIdle { assertEquals(0, repository.replaceCalls); assertEquals(listOf(previous, final), repository.data.readings) }
    }

    @Test fun missingFinalRoutesToOrdinaryAddWithoutAutomaticReplacement() {
        repository.publish(TrackerData(listOf(previous)))
        show()
        openReplacement()
        rule.onNodeWithText(text(R.string.replacement_missing_final)).assertIsDisplayed()
        confirm().assertDoesNotExist()
        rule.onNodeWithText(text(R.string.add_reading)).performClick()
        rule.onNode(hasSetTextAction() and hasText(text(R.string.value_label))).performTextInput("1105")
        rule.onNodeWithText(text(R.string.save)).performClick()
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.runOnIdle { assertTrue(repository.data.replacements.isEmpty()); assertEquals(0, repository.replaceCalls) }
        openReplacement()
        confirm().performScrollTo().assertIsDisplayed()
    }

    @Test fun storageRetryPreservesConfirmationAndPendingRestoreLocksAllActions() {
        val restoration = show()
        openReplacement()
        assertNoReplacementInput()
        val originalDialog = tracker.state.dialog as ReadingDialog.Replacing
        rule.runOnIdle { repository.failReplacement = true }
        confirm().performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.replacement_storage_error)).performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        assertNoReplacementInput()
        rule.onNodeWithText(text(R.string.replacement_storage_error)).performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertEquals(originalDialog.dialogId, (tracker.state.dialog as ReadingDialog.Replacing).dialogId) }
        rule.runOnIdle { repository.failReplacement = false; repository.gate = CompletableDeferred() }
        confirm().performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        assertNoReplacementInput()
        rule.onNode(hasText(text(R.string.date)) and hasText(dateText(date, Locale.getDefault())))
            .performScrollTo().assertIsNotEnabled()
        confirm().performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.cancel)).performScrollTo().assertIsNotEnabled()
        back()
        outside()
        rule.onNode(isDialog()).assertExists()
        rule.runOnIdle {
            tracker.replaceMeter()
            assertEquals(2, repository.replaceCalls)
            repository.gate!!.complete(Unit)
        }
        rule.waitUntil { tracker.state.dialog == ReadingDialog.Closed }
        restoration.emulateSavedInstanceStateRestore()
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.runOnIdle { assertEquals("0".toBigDecimal(), repository.data.readings.last().cubicMetres) }
    }

    @Test fun unavailableTargetsRequireReopenAndNeverOfferConfirmation() {
        show()
        for ((error, message) in listOf(
            ReplacementError.STALE_DATE to R.string.replacement_stale_date,
            ReplacementError.OBSOLETE_TARGET to R.string.replacement_obsolete,
            ReplacementError.NOT_LATEST to R.string.replacement_not_latest,
        )) {
            openReplacement()
            assertNoReplacementInput()
            rule.runOnIdle {
                when (error) {
                    ReplacementError.STALE_DATE -> currentDate = date.plusDays(1)
                    ReplacementError.OBSOLETE_TARGET -> repository.publish(repository.data.copy(
                        readings = listOf(previous, final.copy(cubicMetres = "1106".toBigDecimal())),
                    ))
                    ReplacementError.NOT_LATEST -> repository.publish(repository.data.copy(
                        readings = repository.data.readings + Reading("future", date.plusDays(1), "1107".toBigDecimal()),
                    ))
                    else -> error("Unexpected test case")
                }
            }
            confirm().performScrollTo().performClick()
            rule.onNodeWithText(text(message)).performScrollTo().assertIsDisplayed()
            confirm().assertDoesNotExist()
            rule.onNodeWithText(text(R.string.cancel)).performScrollTo().performClick()
            rule.runOnIdle { currentDate = date; repository.publish(TrackerData(listOf(previous, final))) }
        }
        rule.runOnIdle { assertTrue(repository.data.replacements.isEmpty()) }
    }

    @Test fun observationFailureBlocksConfirmationAndRetryRetainsTargetWithoutInput() {
        show()
        openReplacement()
        assertNoReplacementInput()
        rule.runOnIdle { repository.failObservation.value = true }
        rule.waitUntil { tracker.state.loadState == ReadingLoadState.ERROR }
        confirm().performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("1105.0000", substring = true).performScrollTo().assertIsDisplayed()
        rule.runOnIdle { repository.failObservation.value = false }
        rule.onNode(hasText(text(R.string.retry)) and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        rule.waitUntil { tracker.state.loadState == ReadingLoadState.READY }
        confirm().performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, repository.replaceCalls) }
    }

    @Test fun confirmationRestorationRetainsMatchingDialogButNotPreviousSession() {
        val restoration = show()
        openReplacement()
        val original = tracker.state.dialog
        restoration.emulateSavedInstanceStateRestore()
        assertNoReplacementInput()
        rule.runOnIdle { assertEquals(original, tracker.state.dialog) }
        rule.runOnIdle { tracker.openReplacement() }
        restoration.emulateSavedInstanceStateRestore()
        assertNoReplacementInput()
        rule.runOnIdle { assertNotEquals(original, tracker.state.dialog) }
        rule.runOnIdle {
            tracker = newTracker()
            activeTracker = tracker
        }
        rule.waitUntil { tracker.state.loadState == ReadingLoadState.READY }
        restoration.emulateSavedInstanceStateRestore()
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.runOnIdle { tracker.openReplacement() }
        assertNoReplacementInput()
        rule.runOnIdle { assertEquals(0, repository.replaceCalls) }
    }

    @Test fun toolbarGatesMutationsDuringLoadingFailureAndPendingReplacement() {
        repository.observationGate = CompletableDeferred()
        show(waitReady = false)
        options().assertIsNotEnabled()
        add().assertIsNotEnabled()
        rule.runOnIdle { repository.observationGate!!.complete(Unit) }
        rule.waitUntil { tracker.state.loadState == ReadingLoadState.READY }
        rule.runOnIdle { repository.failObservation.value = true }
        rule.waitUntil { tracker.state.loadState == ReadingLoadState.ERROR }
        options().assertIsNotEnabled()
        add().assertIsNotEnabled()
        rule.runOnIdle { repository.failObservation.value = false }
        rule.onNodeWithText(text(R.string.retry)).performClick()
        rule.waitUntil { tracker.state.loadState == ReadingLoadState.READY }
        openReplacement()
        assertNoReplacementInput()
        rule.runOnIdle { repository.gate = CompletableDeferred() }
        confirm().performScrollTo().performClick()
        options().assertIsNotEnabled()
        add().assertIsNotEnabled()
        rule.runOnIdle {
            val dialog = tracker.state.dialog
            tracker.openAdd()
            tracker.openReplacement()
            tracker.openDelete(previous)
            assertEquals(dialog, tracker.state.dialog)
            repository.gate!!.complete(Unit)
        }
    }

    @Test fun historyDisambiguatesEndpointsAndAllowsCorrectionButExplainsProtection() {
        val replaced = evaluateReplacement(
            repository.data, ReplaceMeterIntent(replacementTarget(repository.data, date), date),
            "new-meter", "initial", "replacement",
        ) as ReplacementValidation.Accepted
        repository.publish(replaced.tracker.copy(
            readings = replaced.tracker.readings + Reading("next", date.plusDays(1), "3".toBigDecimal(), "new-meter"),
        ))
        show()
        rule.onNodeWithText(text(R.string.history_tab)).performClick()
        val initialLabel = context.getString(R.string.meter_role, context.getString(R.string.meter_number, 2), text(R.string.initial_reading))
        val finalLabel = context.getString(R.string.meter_role, context.getString(R.string.meter_number, 1), text(R.string.final_reading))
        val initialRow = rule.onNode(hasText(initialLabel) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        val finalRow = rule.onNode(hasText(finalLabel) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        initialRow.performScrollTo()
        assertTrue(initialRow.fetchSemanticsNode().boundsInRoot.top < finalRow.fetchSemanticsNode().boundsInRoot.top)
        val initialActions = initialRow.fetchSemanticsNode().config
        assertTrue(initialActions[SemanticsActions.OnClick].label!!.contains("Meter 2"))
        assertTrue(initialActions[SemanticsActions.OnClick].label!!.contains("Initial reading"))
        assertTrue(initialActions[SemanticsActions.OnClick].label!!.contains("0 m³"))
        assertTrue(initialActions[SemanticsActions.OnLongClick].label!!.contains(dateText(date, Locale.getDefault())))
        initialRow.performClick()
        rule.onNode(hasSetTextAction() and hasText(text(R.string.value_label))).performTextReplacement("4")
        rule.onNodeWithText(text(R.string.save)).performClick()
        rule.onNodeWithText(text(R.string.above_next)).assertIsDisplayed()
        rule.onNode(hasSetTextAction() and hasText(text(R.string.value_label))).performTextReplacement("1")
        rule.onNodeWithText(text(R.string.save)).performClick()
        rule.waitUntil(5_000) { tracker.state.dialog == ReadingDialog.Closed }
        rule.runOnIdle {
            assertEquals("1".toBigDecimal(), repository.data.readings.single { it.id == "initial" }.cubicMetres)
            assertEquals(replaced.tracker.replacements, repository.data.replacements)
        }
        finalRow.performScrollTo().performClick()
        rule.onNode(hasSetTextAction() and hasText(text(R.string.value_label))).performTextReplacement("1106")
        rule.onNodeWithText(text(R.string.save)).performClick()
        rule.waitUntil(5_000) { tracker.state.dialog == ReadingDialog.Closed }
        rule.runOnIdle {
            assertEquals("1106".toBigDecimal(), repository.data.readings.single { it.id == final.id }.cubicMetres)
            assertEquals(replaced.tracker.replacements, repository.data.replacements)
        }
        for (label in listOf(initialLabel, finalLabel)) {
            rule.onNode(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
                .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
            rule.onNodeWithText(text(R.string.protected_reading)).assertIsDisplayed()
            rule.onNodeWithText(text(R.string.delete)).assertDoesNotExist()
            rule.onNodeWithText(text(R.string.cancel)).performClick()
        }
        rule.runOnIdle { tracker.openDelete(previous) }
        rule.onNodeWithText(text(R.string.delete)).assertIsDisplayed().performClick()
        rule.runOnIdle { assertFalse(repository.data.readings.any { it.id == previous.id }); assertEquals(1, repository.deleteCalls) }
    }

    @Test fun lateBoundaryProtectionReplacesOldDestructiveConfirmation() {
        show()
        rule.runOnIdle { tracker.openDelete(final) }
        rule.onNodeWithText(text(R.string.delete)).assertIsDisplayed()
        rule.runOnIdle {
            val replaced = evaluateReplacement(
                repository.data, ReplaceMeterIntent(replacementTarget(repository.data, date), date),
                "new", "initial", "replacement",
            ) as ReplacementValidation.Accepted
            repository.publish(replaced.tracker)
        }
        rule.onNodeWithText(text(R.string.delete)).performClick()
        rule.onNodeWithText(text(R.string.protected_reading)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.delete)).assertDoesNotExist()
        rule.runOnIdle { assertEquals(3, repository.data.readings.size) }
    }

    @Test fun doubleFontLandscapeKeepsAddKeyboardAndNoInputReplacementScrollReachableWithInsets() {
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        show(doubleFont = true)
        add().performClick()
        rule.onNode(hasSetTextAction() and hasText(text(R.string.value_label)))
            .performScrollTo().performClick().performTextInput("1106")
        rule.onNodeWithText(text(R.string.save)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.cancel)).performScrollTo().assertIsDisplayed().performClick()
        options().performClick()
        rule.onNodeWithText(text(R.string.replace_meter)).assertIsDisplayed().performClick()
        assertNoReplacementInput()
        rule.onNodeWithText(text(R.string.replacement_explanation)).performScrollTo().assertIsDisplayed()
        confirm().performScrollTo().assertIsDisplayed()
        val bounds = confirm().fetchSemanticsNode().boundsInRoot
        val root = rule.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top >= root.top && bounds.bottom <= root.bottom)
        rule.onNodeWithText(text(R.string.cancel)).performScrollTo().assertIsDisplayed().performClick()
        openReplacement()
        outside()
        rule.onNode(isDialog()).assertDoesNotExist()
    }

    private class UiRepository(initial: TrackerData) : ReadingRepository {
        var data = initial
            private set
        private var revision = 0L
        private val snapshots = MutableStateFlow(CommittedSnapshot(revision, data))
        val failObservation = MutableStateFlow(false)
        var failReplacement = false
        var observationGate: CompletableDeferred<Unit>? = null
        var gate: CompletableDeferred<Unit>? = null
        var replaceCalls = 0
        var deleteCalls = 0
        fun publish(next: TrackerData): CommittedSnapshot {
            data = next
            return CommittedSnapshot(++revision, next).also { snapshots.value = it }
        }
        override fun observeReadings(): Flow<CommittedSnapshot> = combine(snapshots, failObservation) { snapshot, failed ->
            if (failed) throw ReadingStorageException(IOException())
            snapshot
        }.onStart { observationGate?.await() }
        override suspend fun replaceMeter(intent: ReplaceMeterIntent): ReplaceMeterResult {
            replaceCalls++
            gate?.await()
            if (failReplacement) throw ReadingStorageException(IOException())
            return when (val result = evaluateReplacement(data, intent, "meter-$replaceCalls", "initial-$replaceCalls", "replacement-$replaceCalls")) {
                is ReplacementValidation.Accepted -> ReplaceMeterResult.Replaced(publish(result.tracker))
                is ReplacementValidation.Rejected -> ReplaceMeterResult.Rejected(result.error)
            }
        }
        override suspend fun delete(id: String): DeleteReadingResult {
            deleteCalls++
            return when (evaluateDeletion(data, id)) {
                DeletionValidation.ALLOWED -> DeleteReadingResult.Deleted(publish(data.copy(readings = data.readings.filterNot { it.id == id })))
                DeletionValidation.MISSING -> DeleteReadingResult.Missing
                DeletionValidation.PROTECTED -> DeleteReadingResult.Protected
            }
        }
        override suspend fun save(intent: SaveReadingIntent): SaveReadingResult {
            val result = when (intent) {
                is SaveReadingIntent.Add -> evaluateSave(data, intent, "saved")
                is SaveReadingIntent.Edit -> evaluateSave(data, intent)
            }
            return when (result) {
                is ReadingValidation.Accepted -> SaveReadingResult.Saved(publish(data.copy(
                    readings = data.readings.filterNot { it.id == result.candidate.id } + result.candidate,
                )))
                is ReadingValidation.Rejected -> SaveReadingResult.Invalid(result.errors)
            }
        }
    }
}
