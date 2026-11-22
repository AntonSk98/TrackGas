package com.trackgas.app.tracking

import com.trackgas.app.temperature.MeterLocation
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class TrackerTab { DASHBOARD, HISTORY }
enum class ReadingLoadState { LOADING, READY, ERROR }

enum class LocationLookupError { NOT_FOUND, UNAVAILABLE }

sealed interface LocationLookupState {
    data object Ready : LocationLookupState
    data object Resolving : LocationLookupState
    data class Error(val reason: LocationLookupError) : LocationLookupState
}

sealed interface MeterLocationDialog {
    data object Closed : MeterLocationDialog
    data class Editing(
        val query: String,
        val selected: MeterLocation?,
        val suggestions: List<MeterLocation> = emptyList(),
        val requestedAfterAdd: Boolean = false,
        val lookup: LocationLookupState = LocationLookupState.Ready,
    ) : MeterLocationDialog
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Selecting(val requestId: String, val tracker: TrackerData) : ExportState
    data class Writing(val requestId: String, val tracker: TrackerData) : ExportState
    data class Complete(val requestId: String, val succeeded: Boolean) : ExportState
}

sealed interface CsvImportState {
    data object Idle : CsvImportState
    data class Selecting(val requestId: String) : CsvImportState
    data class Reading(val requestId: String) : CsvImportState
    data class Preview(val requestId: String, val preview: CsvImportPreview) : CsvImportState
    data class Applying(val requestId: String, val preview: CsvImportPreview) : CsvImportState
    data class Complete(val requestId: String, val affectedMeters: Int) : CsvImportState
    data class Cancelled(val requestId: String) : CsvImportState
    data class Failed(val requestId: String, val error: CsvImportError, val stalePreview: Boolean = false) : CsvImportState
}

/** Values rendered by Dashboard, History, and their dialogs; not the database contents alone. */
data class TrackerState(
    val readings: List<Reading>,
    val period: CalendarPeriod,
    val dashboard: Dashboard,
    val tab: TrackerTab = TrackerTab.DASHBOARD,
    val loadState: ReadingLoadState = ReadingLoadState.LOADING,
    val loadFailure: ReadingFailure? = null,
    val dialog: ReadingDialog = ReadingDialog.Closed,
    val locationDialog: MeterLocationDialog = MeterLocationDialog.Closed,
    val meterLocation: MeterLocation? = null,
    val temperatures: Map<LocalDate, BigDecimal> = emptyMap(),
    val intervalTemperatures: Map<LocalDate, BigDecimal> = emptyMap(),
    val dailyTemperatures: Map<LocalDate, BigDecimal> = emptyMap(),
    val dailyRates: Map<LocalDate, BigDecimal> = emptyMap(),
    val sessionId: String = UUID.randomUUID().toString(),
    val tracker: TrackerData = TrackerData(readings),
    val export: ExportState = ExportState.Idle,
    val csvImport: CsvImportState = CsvImportState.Idle,
    val chartMode: ConsumptionChartMode = ConsumptionChartMode.ABSOLUTE,
    val dashboardLoadState: ReadingLoadState = ReadingLoadState.LOADING,
    val history: HistoryPagingState = HistoryPagingState(),
    val dialogTracker: TrackerData? = null,
    val dialogLoadState: ReadingLoadState = ReadingLoadState.READY,
)
