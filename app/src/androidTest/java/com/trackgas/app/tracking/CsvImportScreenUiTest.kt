package com.trackgas.app.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trackgas.app.R
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CsvImportScreenUiTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.of(2026, 10, 29)
    private val meter = "11111111-1111-4111-8111-111111111111"
    private val period = CalendarPeriod.containing(PeriodType.WEEK, today)
    private val reading = Reading("before", today.minusDays(1), "100".toBigDecimal(), meter)
    private val tracker = TrackerData(listOf(reading), listOf(MeterInstallation(meter)))
    private val preview = CsvImportPreview(
        "preview", 1, listOf(CsvImportMeterChange(meter, 1, 2)), 0, tracker,
        listOf(CsvReadingRow(meter, today, "110".toBigDecimal(), 2)),
    )

    private fun text(id: Int) = context.getString(id)
    private fun state(import: CsvImportState = CsvImportState.Idle) = TrackerState(
        readings = tracker.readings,
        period = period,
        dashboard = dashboard(tracker, period),
        loadState = ReadingLoadState.READY,
        tracker = tracker,
        csvImport = import,
    )

    private fun show(current: TrackerState, onImport: () -> Unit = {}, onConfirm: () -> Unit = {}, onCancel: (String) -> Unit = {}) {
        rule.setContent {
            MaterialTheme {
                TrackerScreen(
                    state = current,
                    currentDate = { today },
                    onSelectTab = {}, onSelectPeriod = {}, onNavigate = {}, onRetryLoad = {},
                    onOpenAdd = {}, onOpenEdit = {}, onOpenDelete = {}, onDismissDialog = {},
                    onClearDialogError = {}, onSave = { _, _, _ -> }, onDelete = {},
                    onOpenPurge = {}, onPurge = {}, onImport = onImport,
                    onConfirmCsvImport = onConfirm, onCancelCsvImport = onCancel,
                )
            }
        }
    }

    @Test fun optionsOffersImportAndSuccessfulClickStartsSelection() {
        var current by mutableStateOf(state())
        var starts = 0
        rule.setContent {
            MaterialTheme {
                TrackerScreen(
                    state = current,
                    currentDate = { today },
                    onSelectTab = {}, onSelectPeriod = {}, onNavigate = {}, onRetryLoad = {},
                    onOpenAdd = {}, onOpenEdit = {}, onOpenDelete = {}, onDismissDialog = {},
                    onClearDialogError = {}, onSave = { _, _, _ -> }, onDelete = {},
                    onOpenPurge = {}, onPurge = {},
                    onImport = { starts++; current = current.copy(csvImport = CsvImportState.Selecting("request")) },
                )
            }
        }
        rule.onNodeWithContentDescription(text(R.string.tracker_options)).performClick()
        rule.onNodeWithText(text(R.string.import_csv)).assertIsDisplayed().assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1, starts)
            assertEquals(CsvImportState.Selecting("request"), current.csvImport)
            assertEquals(tracker, current.tracker)
        }
    }

    @Test fun previewShowsReplacementScopeCountsAndExplicitConfirmationOrCancellation() {
        var confirms = 0
        var cancelled: String? = null
        show(
            state(CsvImportState.Preview("request", preview)),
            onConfirm = { confirms++ },
            onCancel = { cancelled = it },
        )
        rule.onNodeWithText(text(R.string.csv_import_preview_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.csv_import_replacement_warning)).assertIsDisplayed()
        rule.onNodeWithText(meter).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.csv_import_confirm)).assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, confirms) }
        rule.onNodeWithText(text(R.string.cancel)).performClick()
        rule.runOnIdle {
            assertEquals("request", cancelled)
            assertEquals(tracker, preview.baseTracker)
        }
    }

    @Test fun stableUnknownMeterErrorShowsRowAndFieldWithoutThrowableDetails() {
        show(state(CsvImportState.Failed(
            "request", CsvImportError(CsvImportErrorCode.CSV_METER_UNKNOWN, 4, CsvImportField.METER_ID),
        )))
        rule.onNodeWithText(text(R.string.csv_import_error_meter_unknown)).assertIsDisplayed()
        rule.onNodeWithText("Row 4 · meter_id").assertIsDisplayed()
        rule.onNodeWithText("CSV_METER_UNKNOWN").assertDoesNotExist()
    }

    @Test fun invalidRowErrorRetainsPhysicalRowContext() {
        show(state(CsvImportState.Failed(
            "request", CsvImportError(CsvImportErrorCode.CSV_ROW_INVALID, 7),
        )))
        rule.onNodeWithText(text(R.string.csv_import_error_row)).assertIsDisplayed()
        rule.onNodeWithText("Row 7").assertIsDisplayed()
    }

    @Test fun stalePreviewPromptsForNewReviewWithoutShowingInternalCode() {
        show(state(CsvImportState.Failed(
            "request", CsvImportError(CsvImportErrorCode.CSV_IMPORT_FAILED), stalePreview = true,
        )))
        rule.onNodeWithText(text(R.string.csv_import_stale)).assertIsDisplayed()
        rule.onNodeWithText("CSV_IMPORT_FAILED").assertDoesNotExist()
    }

    @Test fun safeFallbackUsesShortMessageWithoutShowingInternalCode() {
        show(state(CsvImportState.Failed(
            "request", CsvImportError(CsvImportErrorCode.CSV_IMPORT_FAILED),
        )))
        rule.onNodeWithText(text(R.string.csv_import_error_generic)).assertIsDisplayed()
        rule.onNodeWithText("CSV_IMPORT_FAILED").assertDoesNotExist()
    }
}
