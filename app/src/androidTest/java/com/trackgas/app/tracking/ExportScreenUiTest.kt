package com.trackgas.app.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trackgas.app.R
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportScreenUiTest {
    @get:Rule val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.of(2026, 10, 29)
    private val reading = Reading("one", today.minusDays(2), "123.450".toBigDecimal())
    private val period = CalendarPeriod.containing(PeriodType.MONTH, today).navigate(-1)
    private var state by mutableStateOf(snapshot())
    private var exports = 0
    private var retryLoads = 0

    private fun snapshot(readings: List<Reading> = listOf(reading)) = TrackerState(
        readings = readings,
        period = period,
        dashboard = dashboard(readings, period),
        loadState = ReadingLoadState.READY,
    )

    private fun text(id: Int) = context.getString(id)

    private fun showScreen() {
        rule.setContent {
            MaterialTheme {
                TrackerScreen(
                    state = state,
                    currentDate = { today },
                    onSelectTab = { state = state.copy(tab = it) },
                    onSelectPeriod = { state = state.copy(period = CalendarPeriod.containing(it, today)) },
                    onNavigate = { state = state.copy(period = state.period.navigate(it)) },
                    onRetryLoad = { retryLoads++; state = state.copy(loadState = ReadingLoadState.LOADING) },
                    onOpenAdd = {},
                    onOpenEdit = {},
                    onOpenDelete = {},
                    onDismissDialog = {},
                    onClearDialogError = {},
                    onSave = { _, _, _ -> },
                    onDelete = {},
                    onOpenPurge = {},
                    onPurge = {},
                    onExport = {
                        exports++
                        state = state.copy(export = ExportState.Selecting("request-$exports", state.tracker))
                    },
                )
            }
        }
    }

    private fun openOptions() =
        rule.onNodeWithContentDescription(text(R.string.tracker_options)).performClick()

    private fun exportItem() = rule.onNodeWithText(text(R.string.export_csv))

    private fun assertMenuOrderAndVisibleIcon() {
        val export = exportItem().assertIsDisplayed().assertIsEnabled()
        val replace = rule.onNodeWithText(text(R.string.replace_meter)).assertIsDisplayed()
        val purge = rule.onNodeWithText(text(R.string.purge_tracking_data)).assertIsDisplayed()
        assertTrue(export.getUnclippedBoundsInRoot().top < replace.getUnclippedBoundsInRoot().top)
        assertTrue(replace.getUnclippedBoundsInRoot().top < purge.getUnclippedBoundsInRoot().top)

        // The leading region must contain drawn pixels, not just an accessible text label.
        val image = export.captureToImage().toPixelMap()
        val background = image[1, 1]
        val density = context.resources.displayMetrics.density
        val left = (12 * density).toInt()
        val right = minOf((40 * density).toInt(), image.width)
        var ink = 0
        for (x in left until right) {
            for (y in image.height / 4 until image.height * 3 / 4) {
                if (image[x, y] != background) ink++
            }
        }
        assertTrue("Export item should have a visible leading icon", ink > 10)
    }

    @Test fun menuOrderIconAndExportPreserveNavigationOnBothTabs() {
        showScreen()
        val original = state.tracker
        for (tab in listOf(TrackerTab.DASHBOARD, TrackerTab.HISTORY)) {
            if (tab == TrackerTab.HISTORY) rule.onNodeWithText(text(R.string.history_tab)).performClick()
            rule.runOnIdle { assertEquals(tab, state.tab) }
            openOptions()
            assertMenuOrderAndVisibleIcon()
            exportItem().performClick()
            rule.runOnIdle {
                assertEquals(tab, state.tab)
                assertEquals(period, state.period)
                assertEquals(original, state.tracker)
                assertEquals(original, (state.export as ExportState.Selecting).tracker)
                state = state.copy(export = ExportState.Idle)
            }
        }
        assertEquals(2, exports)
    }

    @Test fun loadingAndErrorDisableOptionsAndRetryRestoresExport() {
        state = snapshot().copy(loadState = ReadingLoadState.LOADING)
        showScreen()
        for (load in listOf(ReadingLoadState.LOADING, ReadingLoadState.ERROR)) {
            rule.runOnIdle { state = state.copy(loadState = load) }
            rule.onNodeWithContentDescription(text(R.string.tracker_options)).assertIsNotEnabled()
            exportItem().assertDoesNotExist()
        }
        rule.onNodeWithText(text(R.string.retry)).performClick()
        rule.runOnIdle {
            assertEquals(1, retryLoads)
            assertEquals(ReadingLoadState.LOADING, state.loadState)
            state = state.copy(loadState = ReadingLoadState.READY)
        }
        openOptions()
        exportItem().assertIsEnabled()
    }

    @Test fun emptyTrackerIsExportableAndBusyRequestsAreDisabled() {
        state = snapshot(emptyList())
        showScreen()
        openOptions()
        exportItem().assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(TrackerData(), (state.export as ExportState.Selecting).tracker)
        }
        openOptions()
        exportItem().assertIsNotEnabled()
        rule.runOnIdle { state = state.copy(export = ExportState.Writing("request-1", state.tracker)) }
        exportItem().assertIsNotEnabled()
        rule.runOnIdle { state = state.copy(export = ExportState.Idle) }
        exportItem().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(2, exports) }
    }
}
