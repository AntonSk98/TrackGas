package com.trackgas.app.tracking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trackgas.app.temperature.MeterLocation
import com.trackgas.app.temperature.TemperatureServices
import com.trackgas.app.temperature.IntervalTemperatureEntity
import com.trackgas.app.temperature.DailyTemperatureEntity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import com.trackgas.app.reminders.ReminderScheduler
import com.trackgas.app.reminders.ReminderStore
import java.io.OutputStream
import java.io.InputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.logging.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds screen state and coordinates user actions for Dashboard and History.
 * Screens render [state] and call actions such as [save], [delete], and [navigate].
 * Repositories validate and persist changes; this class publishes their committed results
 * together with the selected period, dialog state, and chart values.
 *
 * Reading times are passed through to storage. Cached interval temperatures are adapted
 * to consumption dates here; weather requests run separately through [TemperatureServices].
 * The ViewModel survives screen recreation, but durable data belongs to the repositories.
 */
class TrackerViewModel(
    private val repository: ReadingRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val projectionDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val temperature: TemperatureServices? = null,
    private val reminderStore: ReminderStore? = null,
    private val reminderScheduler: ReminderScheduler? = null,
    private val currentInstant: () -> Instant = { Instant.now() },
    private val currentZone: () -> ZoneId = { ZoneId.systemDefault() },
    private val currentDate: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val initialPeriod = CalendarPeriod.containing(PeriodType.WEEK, today())
    var state by mutableStateOf(TrackerState(emptyList(), initialPeriod, dashboard(emptyList(), initialPeriod)))
        private set
    private var dashboardObservation: Job? = null
    private var suggestionJob: Job? = null
    private var observationGeneration = 0L
    private var projectionGeneration = 0L
    private var temperatureGeneration = 0L
    private var acceptedRevision = Long.MIN_VALUE
    private val exportLoads = mutableMapOf<String, Deferred<TrackerData>>()
    private var latestDashboardTracker: TrackerData? = null
    private var lastProjectionPeriod: CalendarPeriod? = null
    private val historyPager = HistoryPagingController(
        scope = viewModelScope,
        loadPage = { cursor ->
            if (repository.usesPeriodScopedDashboardData) {
                withContext(ioDispatcher) { repository.historyPage(cursor) }
            } else {
                historyPageFromTracker(latestDashboardTracker ?: state.tracker, cursor, HISTORY_PAGE_SIZE)
            }
        },
        fallbackInstallations = { state.tracker.installations },
        onStateChanged = { history -> state = state.copy(history = history) },
    )
    private var intervalMeans: List<IntervalTemperatureEntity> = emptyList()
    private var dailyMeans: List<DailyTemperatureEntity> = emptyList()
    // Identifies the location revision these cached means belong to, not a reading revision.
    private var intervalMeansGeneration: Long? = null

    init {
        observeDashboardData(state.period)
        observeTemperatures()
        temperature?.let { services ->
            viewModelScope.launch {
                services.coordinator.location.collect {
                    temperatureGeneration++
                    state = state.copy(meterLocation = it.location)
                    if (state.dashboardLoadState == ReadingLoadState.READY) project(state.tracker, state.period)
                    refreshTemperatureSnapshot(services)
                }
            }
            services.checks.requestCheck(state.period.range)
        }
    }

    fun today(): LocalDate = currentDate()

    fun selectTab(tab: TrackerTab) {
        state = state.copy(tab = tab)
        if (tab == TrackerTab.HISTORY) historyPager.ensureLoaded()
    }

    fun loadMoreHistory() = historyPager.loadMore()

    fun retryHistory() = historyPager.retry()

    fun selectChartMode(mode: ConsumptionChartMode) {
        state = state.copy(chartMode = mode)
        if (state.dashboardLoadState == ReadingLoadState.READY) project(state.tracker, state.period)
    }

    fun selectPeriod(type: PeriodType) = selectDashboardPeriod(CalendarPeriod.containing(type, today()))

    fun navigate(offset: Long) = selectDashboardPeriod(state.period.navigate(offset))

    fun retryLoad() {
        when {
            state.dialogLoadState == ReadingLoadState.ERROR && state.dialog == ReadingDialog.Closed -> openReplacement()
            state.dialogLoadState == ReadingLoadState.ERROR -> loadDialogTracker()
            state.dashboardLoadState == ReadingLoadState.ERROR || state.loadState == ReadingLoadState.ERROR ->
                observeDashboardData(state.period)
            state.history.error -> historyPager.retry()
        }
    }

    fun requestExport(): String? {
        if (state.loadState != ReadingLoadState.READY || state.export !is ExportState.Idle) return null
        val requestId = newId()
        val tracker = if (repository.usesPeriodScopedDashboardData) TrackerData() else state.tracker.copy(
            readings = state.tracker.readings.toList(),
            installations = state.tracker.installations.toList(),
        )
        state = state.copy(export = ExportState.Selecting(requestId, tracker))
        exportLoads[requestId] = if (repository.usesPeriodScopedDashboardData) {
            viewModelScope.async(ioDispatcher) { repository.completeTracker() }
        } else {
            viewModelScope.async { tracker }
        }
        return requestId
    }

    fun cancelExport(requestId: String) {
        val export = state.export as? ExportState.Selecting ?: return
        if (export.requestId == requestId) {
            exportLoads.remove(requestId)?.cancel()
            state = state.copy(export = ExportState.Idle)
        }
    }

    fun beginExportWrite(requestId: String): TrackerData? {
        val export = state.export as? ExportState.Selecting ?: return null
        if (export.requestId != requestId) return null
        state = state.copy(export = ExportState.Writing(requestId, export.tracker))
        return export.tracker
    }

    fun writeExport(requestId: String, open: () -> OutputStream?) {
        beginExportWrite(requestId) ?: return
        viewModelScope.launch {
            try {
                val tracker = exportLoads[requestId]?.await() ?: return@launch
                val export = state.export as? ExportState.Writing ?: return@launch
                if (export.requestId != requestId) return@launch
                state = state.copy(export = export.copy(tracker = tracker))
                val succeeded = withContext(ioDispatcher) { writeReadingCsv(tracker, open) }
                finishExportWrite(requestId, succeeded)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ReadingStorageException) {
                finishExportWrite(requestId, false)
            }
        }
    }

    fun finishExportWrite(requestId: String, succeeded: Boolean) {
        val export = state.export as? ExportState.Writing ?: return
        if (export.requestId == requestId) {
            exportLoads.remove(requestId)?.cancel()
            state = state.copy(export = ExportState.Complete(requestId, succeeded))
        }
    }

    fun acknowledgeExport(requestId: String) {
        val export = state.export as? ExportState.Complete ?: return
        if (export.requestId == requestId) state = state.copy(export = ExportState.Idle)
    }

    fun requestCsvImport(): String? {
        if (state.loadState != ReadingLoadState.READY || state.dialog.pending ||
            state.csvImport !is CsvImportState.Idle || state.export !is ExportState.Idle
        ) return null
        val requestId = newId()
        state = state.copy(csvImport = CsvImportState.Selecting(requestId))
        return requestId
    }

    fun cancelCsvSelection(requestId: String) {
        val current = state.csvImport as? CsvImportState.Selecting ?: return
        if (current.requestId == requestId) state = state.copy(csvImport = CsvImportState.Cancelled(requestId))
    }

    fun failCsvPicker(requestId: String) {
        val current = state.csvImport as? CsvImportState.Selecting ?: return
        if (current.requestId == requestId) state = state.copy(csvImport = CsvImportState.Failed(
            requestId, CsvImportError(CsvImportErrorCode.CSV_IMPORT_FAILED),
        ))
    }

    fun readCsvImport(requestId: String, open: () -> InputStream?) {
        val current = state.csvImport as? CsvImportState.Selecting ?: return
        if (current.requestId != requestId) return
        state = state.copy(csvImport = CsvImportState.Reading(requestId))
        viewModelScope.launch {
            val decoded = try {
                withContext(ioDispatcher) {
                    val bytes = open()?.use { it.readBytes() } ?: throw IOException()
                    StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                finishCsvImport(requestId, CsvImportState.Failed(
                    requestId, CsvImportError(CsvImportErrorCode.CSV_FILE_READ_FAILED),
                ))
                return@launch
            }
            try {
                when (val result = withContext(ioDispatcher) { repository.previewCsvImport(decoded) }) {
                    is CsvImportPreviewResult.Ready -> finishCsvImport(requestId, CsvImportState.Preview(requestId, result.preview))
                    is CsvImportPreviewResult.Rejected -> finishCsvImport(requestId, CsvImportState.Failed(requestId, result.error))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                finishCsvImport(requestId, CsvImportState.Failed(
                    requestId, CsvImportError(CsvImportErrorCode.CSV_IMPORT_FAILED),
                ))
            }
        }
    }

    fun cancelCsvImport(requestId: String) {
        val current = state.csvImport as? CsvImportState.Preview ?: return
        if (current.requestId == requestId) state = state.copy(csvImport = CsvImportState.Cancelled(requestId))
    }

    fun confirmCsvImport() {
        val preview = state.csvImport as? CsvImportState.Preview ?: return
        if (state.loadState != ReadingLoadState.READY) return
        state = state.copy(csvImport = CsvImportState.Applying(preview.requestId, preview.preview))
        viewModelScope.launch {
            try {
                when (val result = withContext(ioDispatcher) { repository.confirmCsvImport(preview.preview) }) {
                    is CsvImportCommitResult.Committed -> {
                        acceptCommittedSnapshot(result.snapshot)
                        finishCsvImport(preview.requestId, CsvImportState.Complete(preview.requestId, preview.preview.changes.size))
                        scheduleReminderAfterSubmission()
                        temperature?.checks?.requestCheck(state.period.range)
                    }
                    is CsvImportCommitResult.Rejected -> finishCsvImport(
                        preview.requestId, CsvImportState.Failed(preview.requestId, result.error),
                    )
                    CsvImportCommitResult.StalePreview -> finishCsvImport(
                        preview.requestId,
                        CsvImportState.Failed(preview.requestId, CsvImportError(CsvImportErrorCode.CSV_IMPORT_FAILED), stalePreview = true),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                finishCsvImport(preview.requestId, CsvImportState.Failed(
                    preview.requestId, CsvImportError(CsvImportErrorCode.CSV_IMPORT_FAILED),
                ))
            }
        }
    }

    fun acknowledgeCsvImport(requestId: String) {
        if (state.csvImport.let { it is CsvImportState.Complete && it.requestId == requestId ||
                it is CsvImportState.Cancelled && it.requestId == requestId ||
                it is CsvImportState.Failed && it.requestId == requestId }
        ) state = state.copy(csvImport = CsvImportState.Idle)
    }

    private fun finishCsvImport(requestId: String, result: CsvImportState) {
        val currentId = when (val current = state.csvImport) {
            is CsvImportState.Reading -> current.requestId
            is CsvImportState.Applying -> current.requestId
            else -> return
        }
        if (currentId == requestId) state = state.copy(csvImport = result)
    }

    private fun selectDashboardPeriod(period: CalendarPeriod) {
        if (state.period == period && state.dashboardLoadState == ReadingLoadState.READY) return
        val wasReady = state.dashboardLoadState == ReadingLoadState.READY
        state = state.copy(
            period = period,
            dashboard = dashboard(emptyList(), period),
            dailyRates = emptyMap(),
            temperatures = emptyMap(),
            intervalTemperatures = emptyMap(),
            dailyTemperatures = emptyMap(),
            dashboardLoadState = ReadingLoadState.LOADING,
        )
        if (repository.usesPeriodScopedDashboardData) {
            observeDashboardData(period)
        } else {
            latestDashboardTracker?.let { tracker ->
                if (wasReady) {
                    project(tracker, period)
                    state = state.copy(dashboardLoadState = ReadingLoadState.READY)
                } else {
                    projectSelectedPeriod(tracker, period)
                }
            }
        }
        temperature?.checks?.requestCheck(period.range)
    }

    private fun observeDashboardData(period: CalendarPeriod) {
        val generation = ++observationGeneration
        dashboardObservation?.cancel()
        state = state.copy(
            loadState = if (state.loadState == ReadingLoadState.ERROR) ReadingLoadState.LOADING else state.loadState,
            dashboardLoadState = ReadingLoadState.LOADING,
            loadFailure = null,
        )
        dashboardObservation = viewModelScope.launch {
            try {
                repository.observeDashboardData(period.range).collect { snapshot ->
                    if (generation != observationGeneration ||
                        (repository.usesPeriodScopedDashboardData && period != state.period)
                    ) return@collect
                    val tracker = snapshot.tracker
                    val selectedPeriod = if (repository.usesPeriodScopedDashboardData) period else state.period
                    val unchanged = sameTracker(tracker, state.tracker)
                    val revision = snapshot.revision
                    if (revision != null && revision < acceptedRevision) return@collect
                    if (unchanged && selectedPeriod == lastProjectionPeriod) {
                        latestDashboardTracker = tracker
                        state = state.copy(
                            loadState = if (state.loadState == ReadingLoadState.LOADING) ReadingLoadState.READY else state.loadState,
                            dashboardLoadState = ReadingLoadState.READY,
                            loadFailure = null,
                        )
                        if (revision != null) acceptedRevision = maxOf(acceptedRevision, revision)
                        return@collect
                    }
                    if (revision != null) acceptedRevision = revision
                    latestDashboardTracker = tracker
                    state = state.copy(readings = tracker.readings, tracker = tracker)
                    state = state.copy(dashboardLoadState = ReadingLoadState.LOADING)
                    temperature?.let {
                        val temperaturePeriod = if (repository.usesPeriodScopedDashboardData) period else state.period
                        refreshTemperatureSnapshot(it, tracker, temperaturePeriod.range, projectAfterRefresh = false)
                    }
                    val projection = ++projectionGeneration
                    while (generation == observationGeneration && projection == projectionGeneration) {
                        val selectedPeriod = if (repository.usesPeriodScopedDashboardData) period else state.period
                        val input = projectionInput(tracker, selectedPeriod)
                        val prepared = withContext(projectionDispatcher) { prepareDashboardProjection(input) }
                        if (generation != observationGeneration || projection != projectionGeneration ||
                            (repository.usesPeriodScopedDashboardData && period != state.period) ||
                            (!repository.usesPeriodScopedDashboardData && selectedPeriod != state.period)
                        ) return@collect
                        if (input.temperatureGeneration != temperatureGeneration || input.chartMode != state.chartMode) {
                            continue
                        }
                        publishProjection(input, prepared)
                        state = state.copy(
                            loadState = if (state.loadState == ReadingLoadState.LOADING) ReadingLoadState.READY else state.loadState,
                            dashboardLoadState = ReadingLoadState.READY,
                            loadFailure = null,
                        )
                        latestDashboardTracker = snapshot.tracker
                        break
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ReadingStorageException) {
                if (generation == observationGeneration) {
                    state = state.copy(
                        loadState = ReadingLoadState.ERROR,
                        dashboardLoadState = ReadingLoadState.ERROR,
                        loadFailure = ReadingFailure(error.category, ReadingAction.LOAD),
                    )
                }
            }
        }
    }

    private fun projectSelectedPeriod(tracker: TrackerData, period: CalendarPeriod) {
        val generation = ++projectionGeneration
        viewModelScope.launch {
            while (generation == projectionGeneration && period == state.period) {
                val input = projectionInput(tracker, period)
                val projection = withContext(projectionDispatcher) { prepareDashboardProjection(input) }
                if (generation != projectionGeneration || period != state.period) return@launch
                if (input.temperatureGeneration != temperatureGeneration || input.chartMode != state.chartMode) continue
                publishProjection(input, projection)
                state = state.copy(
                    loadState = if (state.loadState == ReadingLoadState.LOADING) ReadingLoadState.READY else state.loadState,
                    dashboardLoadState = ReadingLoadState.READY,
                    loadFailure = if (state.loadState == ReadingLoadState.LOADING) null else state.loadFailure,
                )
                latestDashboardTracker = tracker
                return@launch
            }
        }
    }

    private fun observeTemperatures() {
        val services = temperature ?: return
        viewModelScope.launch {
            try {
                services.temperatures.observeChanges().collect {
                    // Room emissions are invalidation signals; their payload may predate a location clear.
                    refreshTemperatureSnapshot(services)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Logger.getLogger(TrackerViewModel::class.java.name)
                    .warning("Could not observe stored temperatures")
            }
        }

    }

    /** Reloads cached means without accepting a result captured before a location change. */
    private suspend fun refreshTemperatureSnapshot(
        services: TemperatureServices,
        tracker: TrackerData = state.tracker,
        range: DateRange = state.period.range,
        projectAfterRefresh: Boolean = true,
    ) {
        try {
            val snapshot = withContext(ioDispatcher) { services.coordinator.snapshot(tracker, range) }
            if (snapshot.location != services.coordinator.location.value || range != state.period.range) return
            intervalMeans = snapshot.intervalMeans
            dailyMeans = snapshot.dailyMeans
            intervalMeansGeneration = snapshot.location.generation
            temperatureGeneration++
            if (projectAfterRefresh && state.dashboardLoadState == ReadingLoadState.READY) {
                project(state.tracker, state.period)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Logger.getLogger(TrackerViewModel::class.java.name).warning("Could not read stored temperatures")
        }
    }

    /** Captures the initial observation time once, so an open form does not follow the clock. */
    fun openAdd() {
        val captured = currentInstant().truncatedTo(ChronoUnit.MINUTES)
        val zone = currentZone()
        openWithCompleteTracker(
            ReadingDialog.Editing(newId(), ReadingTarget.Add(captured.atZone(zone).toLocalDate(), captured, zone)),
        )
    }

    fun openEdit(reading: Reading) =
        openWithCompleteTracker(ReadingDialog.Editing(newId(), ReadingTarget.Edit(reading)))

    fun openDelete(reading: Reading) = open(ReadingDialog.ConfirmingDeletion(newId(), reading))

    fun openPurge() = open(ReadingDialog.ConfirmingPurge(newId()))

    fun openMeterLocation() {
        if (state.loadState != ReadingLoadState.READY || state.dialog.pending) return
        val saved = temperature?.locationStore?.read()
        state = state.copy(
            meterLocation = saved,
            locationDialog = MeterLocationDialog.Editing(saved?.displayName.orEmpty(), saved),
        )
    }

    fun updateMeterLocationQuery(query: String) {
        val dialog = state.locationDialog as? MeterLocationDialog.Editing ?: return
        val placeName = query.filter { it.isLetter() || it.isWhitespace() || it in PLACE_PUNCTUATION }
        if (placeName == dialog.query) return
        suggestionJob?.cancel()
        // Typing invalidates the previous pick: only a chosen suggestion can be saved.
        state = state.copy(
            locationDialog = dialog.copy(
                query = placeName,
                selected = null,
                suggestions = emptyList(),
                lookup = LocationLookupState.Ready,
            ),
        )
        val search = placeName.trim()
        if (search.length >= MIN_SUGGESTION_QUERY_LENGTH) {
            suggestionJob = viewModelScope.launch {
                delay(SUGGESTION_DEBOUNCE_MILLIS)
                loadSuggestions(search)
            }
        }
    }

    fun selectMeterLocationSuggestion(location: MeterLocation) {
        suggestionJob?.cancel()
        updateLocationDialog {
            it.copy(query = location.displayName, selected = location, suggestions = emptyList(), lookup = LocationLookupState.Ready)
        }
    }

    fun dismissMeterLocationDialog() {
        suggestionJob?.cancel()
        state = state.copy(locationDialog = MeterLocationDialog.Closed)
    }

    fun saveMeterLocation() {
        val location = (state.locationDialog as? MeterLocationDialog.Editing)?.selected ?: return
        suggestionJob?.cancel()
        viewModelScope.launch {
            try {
                val services = requireNotNull(temperature) { "Temperature services are not configured" }
                withContext(ioDispatcher) { services.coordinator.save(location) }
                state = state.copy(meterLocation = location, locationDialog = MeterLocationDialog.Closed)
                services.checks.requestCheck(state.period.range)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                updateLocationDialog { it.copy(lookup = LocationLookupState.Error(LocationLookupError.UNAVAILABLE)) }
            }
        }
    }

    private suspend fun loadSuggestions(query: String) {
        updateLocationDialog { it.copy(lookup = LocationLookupState.Resolving) }
        val lookup = try {
            val services = requireNotNull(temperature) { "Temperature services are not configured" }
            if (!services.networkMonitor.isOnline()) {
                LocationLookupState.Error(LocationLookupError.UNAVAILABLE)
            } else {
                val places = withContext(ioDispatcher) { services.locationResolver.suggest(query) }
                updateLocationDialog { it.copy(suggestions = places) }
                if (places.isEmpty()) LocationLookupState.Error(LocationLookupError.NOT_FOUND) else LocationLookupState.Ready
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            LocationLookupState.Error(LocationLookupError.UNAVAILABLE)
        }
        updateLocationDialog { it.copy(lookup = lookup) }
    }

    private fun updateLocationDialog(transform: (MeterLocationDialog.Editing) -> MeterLocationDialog.Editing) {
        val dialog = state.locationDialog as? MeterLocationDialog.Editing ?: return
        state = state.copy(locationDialog = transform(dialog))
    }

    fun openReplacement() {
        if (state.loadState != ReadingLoadState.READY || state.dialog.pending ||
            state.dialogLoadState == ReadingLoadState.LOADING
        ) return
        val requestId = newId()
        if (!repository.usesPeriodScopedDashboardData) {
            val date = today()
            val target = replacementTarget(state.tracker, date) ?: return
            val phase = replacementTargetError(state.tracker, target, date)?.let { ReplacementPhase.Unavailable(it) }
                ?: ReplacementPhase.Ready
            state = state.copy(
                dialog = ReadingDialog.Replacing(requestId, target, phase),
                dialogTracker = null,
                dialogLoadState = ReadingLoadState.READY,
            )
            return
        }
        state = state.copy(dialogLoadState = ReadingLoadState.LOADING, loadFailure = null)
        viewModelScope.launch {
            try {
                val tracker = withContext(ioDispatcher) { repository.completeTracker() }
                if (state.dialogLoadState != ReadingLoadState.LOADING || state.dialog !is ReadingDialog.Closed) return@launch
                val date = today()
                val target = replacementTarget(tracker, date)
                if (target == null) {
                    state = state.copy(dialogLoadState = ReadingLoadState.READY)
                    return@launch
                }
                val phase = replacementTargetError(tracker, target, date)?.let { ReplacementPhase.Unavailable(it) }
                    ?: ReplacementPhase.Ready
                state = state.copy(
                    dialog = ReadingDialog.Replacing(requestId, target, phase),
                    dialogTracker = tracker,
                    dialogLoadState = ReadingLoadState.READY,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: ReadingStorageException) {
                state = state.copy(
                    dialogLoadState = ReadingLoadState.ERROR,
                    loadFailure = ReadingFailure(error.category, ReadingAction.LOAD),
                )
            }
        }
    }

    private fun open(dialog: ReadingDialog) {
        if (state.loadState == ReadingLoadState.READY && !state.dialog.pending) {
            state = state.copy(dialog = dialog, dialogTracker = null, dialogLoadState = ReadingLoadState.READY)
        }
    }

    private fun openWithCompleteTracker(dialog: ReadingDialog.Editing) {
        if (state.loadState != ReadingLoadState.READY || state.dialog.pending) return
        if (!repository.usesPeriodScopedDashboardData) {
            state = state.copy(
                dialog = dialog,
                dialogTracker = null,
                dialogLoadState = ReadingLoadState.READY,
            )
            return
        }
        state = state.copy(
            dialog = dialog,
            dialogTracker = null,
            dialogLoadState = ReadingLoadState.LOADING,
            loadFailure = null,
        )
        loadDialogTracker()
    }

    private fun loadDialogTracker() {
        val dialog = state.dialog
        val dialogId = dialog.identity() ?: return
        state = state.copy(dialogTracker = null, dialogLoadState = ReadingLoadState.LOADING, loadFailure = null)
        viewModelScope.launch {
            try {
                val tracker = withContext(ioDispatcher) { repository.completeTracker() }
                if (state.dialog.identity() != dialogId) return@launch
                state = state.copy(dialogTracker = tracker, dialogLoadState = ReadingLoadState.READY)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ReadingStorageException) {
                if (state.dialog.identity() == dialogId) {
                    state = state.copy(
                        dialogLoadState = ReadingLoadState.ERROR,
                        loadFailure = ReadingFailure(error.category, ReadingAction.LOAD),
                    )
                }
            }
        }
    }

    private fun ReadingDialog.identity(): String? = when (this) {
        ReadingDialog.Closed -> null
        is ReadingDialog.Editing -> dialogId
        is ReadingDialog.ConfirmingDeletion -> dialogId
        is ReadingDialog.Replacing -> dialogId
        is ReadingDialog.ConfirmingPurge -> dialogId
    }

    fun dismissDialog() {
        if (!state.dialog.pending) state = state.copy(
            dialog = ReadingDialog.Closed,
            dialogTracker = null,
            dialogLoadState = ReadingLoadState.READY,
        )
    }

    fun clearDialogError() {
        val dialog = state.dialog
        if (dialog is ReadingDialog.Editing && dialog.phase !is SavePhase.Pending) {
            state = state.copy(dialog = dialog.copy(phase = SavePhase.Ready))
        } else if (dialog is ReadingDialog.Replacing &&
            dialog.phase == ReplacementPhase.Unavailable(ReplacementError.INVALID_STARTING_READING)
        ) {
            state = state.copy(dialog = dialog.copy(phase = ReplacementPhase.Ready))
        }
    }

    fun replaceMeter(input: String, locale: Locale) {
        val dialog = state.dialog as? ReadingDialog.Replacing ?: return
        if (state.loadState != ReadingLoadState.READY || state.dialogLoadState != ReadingLoadState.READY ||
            dialog.pending || dialog.phase is ReplacementPhase.Unavailable
        ) return
        val request = ReadingRequest(state.sessionId, dialog.dialogId, newId())
        val observedAt = currentInstant().truncatedTo(ChronoUnit.MINUTES)
        val zone = currentZone()
        val intent = ReplaceMeterIntent(
            dialog.target, today(), parseMeter(input, locale),
            observedAt.atZone(zone).toLocalTime().toString(), zone, observedAt,
        )
        state = state.copy(dialog = dialog.copy(phase = ReplacementPhase.Pending(request.requestId)))
        viewModelScope.launch {
            try {
                when (val result = repository.replaceMeter(intent)) {
                    is ReplaceMeterResult.Rejected -> finishReplacement(request, ReplacementPhase.Unavailable(result.error))
                    is ReplaceMeterResult.Replaced -> {
                        acceptCommittedSnapshot(result.snapshot)
                        finishReplacement(request, null)
                        scheduleReminderAfterSubmission()
                        temperature?.checks?.requestCheck(state.period.range)
                    }
                }
            } catch (error: ReadingStorageException) {
                finishReplacement(request, ReplacementPhase.Failed(ReadingFailure(error.category, ReadingAction.REPLACE)))
            }
        }
    }

    fun save(date: LocalDate?, input: String, locale: Locale, note: String? = null, time: String, zone: ZoneId) {
        val dialog = state.dialog as? ReadingDialog.Editing ?: return
        if (state.loadState != ReadingLoadState.READY || state.dialogLoadState != ReadingLoadState.READY || dialog.pending) return
        val request = ReadingRequest(state.sessionId, dialog.dialogId, newId())
        val meter = parseMeter(input, locale)
        val intent = when (val target = dialog.target) {
            is ReadingTarget.Add -> SaveReadingIntent.Add(date, meter, currentInstant().atZone(zone).toLocalDate(), time, zone, currentInstant())
            is ReadingTarget.Edit -> SaveReadingIntent.Edit(target.reading.id, meter,
                currentInstant().atZone(target.reading.zone).toLocalDate(), note, time, currentInstant())
        }
        state = state.copy(dialog = dialog.copy(phase = SavePhase.Pending(request.requestId)))
        viewModelScope.launch {
            try {
                when (val result = repository.save(intent)) {
                    is SaveReadingResult.Invalid -> finishSave(request, SavePhase.Invalid(result.errors))
                    is SaveReadingResult.Saved -> {
                        acceptCommittedSnapshot(result.snapshot)
                        finishSave(request, null)
                        scheduleReminderAfterSubmission()
                        temperature?.checks?.requestCheck(state.period.range)
                        if (dialog.target is ReadingTarget.Add) requestLocationAfterAdd()
                    }
                }
            } catch (error: ReadingStorageException) {
                finishSave(request, SavePhase.Failed(ReadingFailure(error.category, ReadingAction.SAVE)))
            }
        }
    }

    fun delete() {
        val dialog = state.dialog as? ReadingDialog.ConfirmingDeletion ?: return
        if (state.loadState != ReadingLoadState.READY || dialog.pending) return
        val request = ReadingRequest(state.sessionId, dialog.dialogId, newId())
        state = state.copy(dialog = dialog.copy(phase = DeletePhase.Pending(request.requestId)))
        viewModelScope.launch {
            try {
                when (val result = repository.delete(dialog.reading.id)) {
                    DeleteReadingResult.Missing -> finishDelete(request, DeletePhase.Missing)
                    is DeleteReadingResult.Deleted -> {
                        acceptCommittedSnapshot(result.snapshot)
                        finishDelete(request, null)
                        temperature?.checks?.requestCheck(state.period.range)
                    }
                }
            } catch (error: ReadingStorageException) {
                finishDelete(request, DeletePhase.Failed(ReadingFailure(error.category, ReadingAction.DELETE)))
            }
        }
    }

    fun purge() {
        val dialog = state.dialog as? ReadingDialog.ConfirmingPurge ?: return
        if (state.loadState != ReadingLoadState.READY || dialog.pending) return
        val request = ReadingRequest(state.sessionId, dialog.dialogId, newId())
        state = state.copy(dialog = dialog.copy(phase = PurgePhase.Pending(request.requestId)))
        viewModelScope.launch {
            try {
                acceptCommittedSnapshot(repository.purge())
                clearReminder()
                finishPurge(request, null)
            } catch (error: ReadingStorageException) {
                finishPurge(request, PurgePhase.Failed(ReadingFailure(error.category, ReadingAction.PURGE)))
            }
        }
    }

    private fun finishPurge(request: ReadingRequest, phase: PurgePhase?) {
        val dialog = state.dialog.completePurge(request, state.sessionId, phase)
        state = state.copy(
            dialog = dialog,
            dialogTracker = if (dialog == ReadingDialog.Closed) null else state.dialogTracker,
            dialogLoadState = if (dialog == ReadingDialog.Closed) ReadingLoadState.READY else state.dialogLoadState,
        )
    }

    private fun scheduleReminderAfterSubmission() {
        reminderStore?.setLastSubmissionMillis(System.currentTimeMillis())
        reminderScheduler?.schedule()
    }

    private fun clearReminder() {
        reminderStore?.clearLastSubmissionMillis()
        reminderScheduler?.cancel()
    }

    private fun finishSave(request: ReadingRequest, phase: SavePhase?) {
        val dialog = state.dialog.completeSave(request, state.sessionId, phase)
        state = state.copy(
            dialog = dialog,
            dialogTracker = if (dialog == ReadingDialog.Closed) null else state.dialogTracker,
            dialogLoadState = if (dialog == ReadingDialog.Closed) ReadingLoadState.READY else state.dialogLoadState,
        )
    }

    private fun finishDelete(request: ReadingRequest, phase: DeletePhase?) {
        state = state.copy(dialog = state.dialog.completeDelete(request, state.sessionId, phase))
    }

    private fun finishReplacement(request: ReadingRequest, phase: ReplacementPhase?) {
        val dialog = state.dialog.completeReplacement(request, state.sessionId, phase)
        state = state.copy(
            dialog = dialog,
            dialogTracker = if (dialog == ReadingDialog.Closed) null else state.dialogTracker,
            dialogLoadState = if (dialog == ReadingDialog.Closed) ReadingLoadState.READY else state.dialogLoadState,
        )
    }

    private fun newId() = UUID.randomUUID().toString()

    private fun requestLocationAfterAdd() {
        val saved = temperature?.locationStore?.read() ?: state.meterLocation
        if (saved == null && temperature?.networkMonitor?.isOnline() == true) {
            state = state.copy(
                locationDialog = MeterLocationDialog.Editing(
                    query = "",
                    selected = null,
                    requestedAfterAdd = true,
                ),
            )
        }
    }

    private suspend fun acceptCommittedSnapshot(snapshot: CommittedSnapshot): Boolean {
        if (snapshot.revision < acceptedRevision) return false
        if (snapshot.revision == acceptedRevision) return true
        acceptedRevision = snapshot.revision
        val tracker = if (repository.usesPeriodScopedDashboardData) {
            dashboardDataForPeriod(snapshot.tracker, state.period.range)
        } else {
            snapshot.tracker
        }

        latestDashboardTracker = tracker
        if (sameTracker(tracker, state.tracker) && state.period == lastProjectionPeriod) {
            historyPager.refreshAfterMutation(state.tab == TrackerTab.HISTORY)
            return true
        }
        val period = state.period
        val generation = ++projectionGeneration
        while (generation == projectionGeneration && period == state.period) {
            val input = projectionInput(tracker, period)
            val projection = withContext(projectionDispatcher) { prepareDashboardProjection(input) }
            if (generation != projectionGeneration || period != state.period) return false
            if (input.temperatureGeneration != temperatureGeneration || input.chartMode != state.chartMode) continue
            publishProjection(input, projection, preserveEqualDashboard = true)
            state = state.copy(
                loadState = if (state.loadState == ReadingLoadState.LOADING) ReadingLoadState.READY else state.loadState,
                dashboardLoadState = ReadingLoadState.READY,
                loadFailure = if (state.loadState == ReadingLoadState.LOADING) null else state.loadFailure,
            )
            historyPager.refreshAfterMutation(state.tab == TrackerTab.HISTORY)
            return true
        }
        return false
    }

    private fun sameTracker(first: TrackerData, second: TrackerData): Boolean =
        first.readings.size == second.readings.size &&
            first.installations.size == second.installations.size &&
            first.readings.associateBy { it.id } == second.readings.associateBy { it.id } &&
            first.installations.associateBy { it.id } == second.installations.associateBy { it.id }

    /**
     * Rebuilds History ordering, gas buckets, and date-keyed temperatures from the same tracker.
     * Location revision checks hide old weather immediately, even before cache clearing finishes.
     */
    private fun project(tracker: TrackerData, period: CalendarPeriod) {
        if (state.loadState != ReadingLoadState.LOADING) projectionGeneration++
        val input = projectionInput(tracker, period)
        publishProjection(input, prepareDashboardProjection(input))
    }

    private fun projectionInput(tracker: TrackerData, period: CalendarPeriod): DashboardProjectionInput {
        val weatherLocation = temperature?.coordinator?.location?.value
        val location = weatherLocation
            ?.takeIf { it.cacheReady && it.generation == intervalMeansGeneration }
            ?.location
        return DashboardProjectionInput(
            tracker = tracker,
            period = period,
            chartMode = state.chartMode,
            intervalMeans = intervalMeans,
            dailyMeans = dailyMeans,
            location = location,
            temperatureGeneration = temperatureGeneration,
        )
    }

    private fun publishProjection(
        input: DashboardProjectionInput,
        projection: DashboardProjection,
        preserveEqualDashboard: Boolean = false,
    ) {
        lastProjectionPeriod = input.period
        state = state.copy(
            readings = projection.readings,
            tracker = input.tracker,
            period = input.period,
            dashboard = if (preserveEqualDashboard && projection.dashboard == state.dashboard) {
                state.dashboard
            } else {
                projection.dashboard
            },
            dailyRates = projection.dailyRates,
            temperatures = projection.temperatures,
            intervalTemperatures = projection.intervalTemperatures,
            dailyTemperatures = projection.dailyTemperatures,
        )
    }

    companion object {
        private const val PLACE_PUNCTUATION = ",.'-"
        private const val MIN_SUGGESTION_QUERY_LENGTH = 2
        private const val SUGGESTION_DEBOUNCE_MILLIS = 400L
        fun factory(
            repository: ReadingRepository,
            temperature: TemperatureServices? = null,
            reminderStore: ReminderStore? = null,
            reminderScheduler: ReminderScheduler? = null,
            projectionDispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == TrackerViewModel::class.java)
                return modelClass.cast(TrackerViewModel(
                    repository = repository,
                    projectionDispatcher = projectionDispatcher,
                    temperature = temperature,
                    reminderStore = reminderStore,
                    reminderScheduler = reminderScheduler,
                ))!!
            }
        }
    }
}
