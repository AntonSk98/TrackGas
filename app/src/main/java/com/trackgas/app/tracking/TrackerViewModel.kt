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
import com.trackgas.app.temperature.TemperatureAggregation
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import com.trackgas.app.reminders.ReminderScheduler
import com.trackgas.app.reminders.ReminderStore
import java.io.OutputStream
import java.io.InputStream
import java.io.IOException
import java.math.BigDecimal
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val sessionId: String = UUID.randomUUID().toString(),
    val tracker: TrackerData = TrackerData(readings),
    val export: ExportState = ExportState.Idle,
    val csvImport: CsvImportState = CsvImportState.Idle,
    val chartMode: ConsumptionChartMode = ConsumptionChartMode.DAILY_ESTIMATE,
)

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
    private var observation: Job? = null
    private var suggestionJob: Job? = null
    private var observationGeneration = 0L
    private var acceptedRevision = Long.MIN_VALUE
    private var intervalMeans: List<IntervalTemperatureEntity> = emptyList()
    // Identifies the location revision these cached means belong to, not a reading revision.
    private var intervalMeansGeneration: Long? = null

    init {
        observeReadings()
        observeTemperatures()
        temperature?.let { services ->
            viewModelScope.launch {
                services.coordinator.location.collect {
                    state = state.copy(meterLocation = it.location)
                    project(state.tracker, state.period)
                    refreshTemperatureSnapshot(services)
                }
            }
            services.checks.requestCheck()
        }
    }

    fun today(): LocalDate = currentDate()

    fun selectTab(tab: TrackerTab) {
        state = state.copy(tab = tab)
    }

    fun selectChartMode(mode: ConsumptionChartMode) {
        state = state.copy(chartMode = mode)
    }

    fun selectPeriod(type: PeriodType) = project(state.tracker, CalendarPeriod.containing(type, today()))

    fun navigate(offset: Long) = project(state.tracker, state.period.navigate(offset))

    fun retryLoad() {
        if (state.loadState == ReadingLoadState.ERROR) observeReadings()
    }

    fun requestExport(): String? {
        if (state.loadState != ReadingLoadState.READY || state.export !is ExportState.Idle) return null
        val requestId = newId()
        val tracker = state.tracker
        state = state.copy(export = ExportState.Selecting(requestId, tracker.copy(
            readings = tracker.readings.toList(),
            installations = tracker.installations.toList(),
        )))
        return requestId
    }

    fun cancelExport(requestId: String) {
        val export = state.export as? ExportState.Selecting ?: return
        if (export.requestId == requestId) state = state.copy(export = ExportState.Idle)
    }

    fun beginExportWrite(requestId: String): TrackerData? {
        val export = state.export as? ExportState.Selecting ?: return null
        if (export.requestId != requestId) return null
        state = state.copy(export = ExportState.Writing(requestId, export.tracker))
        return export.tracker
    }

    fun writeExport(requestId: String, open: () -> OutputStream?) {
        val snapshot = beginExportWrite(requestId) ?: return
        viewModelScope.launch {
            val succeeded = withContext(ioDispatcher) { writeReadingCsv(snapshot, open) }
            finishExportWrite(requestId, succeeded)
        }
    }

    fun finishExportWrite(requestId: String, succeeded: Boolean) {
        val export = state.export as? ExportState.Writing ?: return
        if (export.requestId == requestId) state = state.copy(export = ExportState.Complete(requestId, succeeded))
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
                        temperature?.checks?.requestCheck()
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

    private fun observeReadings() {
        val generation = ++observationGeneration
        observation?.cancel()
        state = state.copy(loadState = ReadingLoadState.LOADING, loadFailure = null)
        observation = viewModelScope.launch {
            try {
                repository.observeReadings().collect { snapshot ->
                    if (generation != observationGeneration) return@collect
                    acceptCommittedSnapshot(snapshot)
                    state = state.copy(loadState = ReadingLoadState.READY, loadFailure = null)
                }
            } catch (error: ReadingStorageException) {
                if (generation == observationGeneration) {
                    state = state.copy(loadState = ReadingLoadState.ERROR, loadFailure = ReadingFailure(error.category, ReadingAction.LOAD))
                }
            }
        }
    }

    private fun observeTemperatures() {
        val services = temperature ?: return
        viewModelScope.launch {
            try {
                services.temperatures.observe().collect {
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
    private suspend fun refreshTemperatureSnapshot(services: TemperatureServices) {
        try {
            val (captured, temperatures) = withContext(ioDispatcher) { services.coordinator.snapshot() }
            if (captured != services.coordinator.location.value) return
            intervalMeans = temperatures
            intervalMeansGeneration = captured.generation
            project(state.tracker, state.period)
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
        open(ReadingDialog.Editing(newId(), ReadingTarget.Add(captured.atZone(zone).toLocalDate(), captured, zone)))
    }

    fun openEdit(reading: Reading) = open(ReadingDialog.Editing(newId(), ReadingTarget.Edit(reading)))

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
                services.checks.requestCheck()
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
        if (state.loadState != ReadingLoadState.READY || state.dialog.pending) return
        val date = today()
        val target = replacementTarget(state.tracker, date)
        val phase = replacementTargetError(state.tracker, target, date)?.let { ReplacementPhase.Unavailable(it) }
            ?: ReplacementPhase.Ready
        open(ReadingDialog.Replacing(newId(), target, phase))
    }

    private fun open(dialog: ReadingDialog) {
        if (state.loadState == ReadingLoadState.READY && !state.dialog.pending) state = state.copy(dialog = dialog)
    }

    fun dismissDialog() {
        if (!state.dialog.pending) state = state.copy(dialog = ReadingDialog.Closed)
    }

    fun clearDialogError() {
        val dialog = state.dialog
        if (dialog is ReadingDialog.Editing && dialog.phase !is SavePhase.Pending) {
            state = state.copy(dialog = dialog.copy(phase = SavePhase.Ready))
        }
    }

    fun replaceMeter() {
        val dialog = state.dialog as? ReadingDialog.Replacing ?: return
        if (state.loadState != ReadingLoadState.READY || dialog.pending || dialog.phase is ReplacementPhase.Unavailable) return
        val request = ReadingRequest(state.sessionId, dialog.dialogId, newId())
        val intent = ReplaceMeterIntent(dialog.target, today())
        state = state.copy(dialog = dialog.copy(phase = ReplacementPhase.Pending(request.requestId)))
        viewModelScope.launch {
            try {
                when (val result = repository.replaceMeter(intent)) {
                    is ReplaceMeterResult.Rejected -> finishReplacement(request, ReplacementPhase.Unavailable(result.error))
                    is ReplaceMeterResult.Replaced -> {
                        acceptCommittedSnapshot(result.snapshot)
                        finishReplacement(request, null)
                        scheduleReminderAfterSubmission()
                    }
                }
            } catch (error: ReadingStorageException) {
                finishReplacement(request, ReplacementPhase.Failed(ReadingFailure(error.category, ReadingAction.REPLACE)))
            }
        }
    }

    fun save(date: LocalDate?, input: String, locale: Locale, note: String? = null, time: String, zone: ZoneId) {
        val dialog = state.dialog as? ReadingDialog.Editing ?: return
        if (state.loadState != ReadingLoadState.READY || dialog.pending) return
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
                        temperature?.checks?.requestCheck()
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
                        temperature?.checks?.requestCheck()
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
        state = state.copy(dialog = state.dialog.completePurge(request, state.sessionId, phase))
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
        state = state.copy(dialog = state.dialog.completeSave(request, state.sessionId, phase))
    }

    private fun finishDelete(request: ReadingRequest, phase: DeletePhase?) {
        state = state.copy(dialog = state.dialog.completeDelete(request, state.sessionId, phase))
    }

    private fun finishReplacement(request: ReadingRequest, phase: ReplacementPhase?) {
        state = state.copy(dialog = state.dialog.completeReplacement(request, state.sessionId, phase))
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

    private fun acceptCommittedSnapshot(snapshot: CommittedSnapshot) {
        if (snapshot.revision <= acceptedRevision) return
        acceptedRevision = snapshot.revision
        // All three collections matter; reading equality includes exact decimal scale.
        if (snapshot.tracker.readings.size == state.tracker.readings.size &&
            snapshot.tracker.installations.size == state.tracker.installations.size &&
            snapshot.readings.associateBy { it.id } == state.tracker.readings.associateBy { it.id } &&
            snapshot.tracker.installations.associateBy { it.id } == state.tracker.installations.associateBy { it.id }
        ) return
        project(snapshot.tracker, state.period)
    }

    /**
     * Rebuilds History ordering, gas buckets, and date-keyed temperatures from the same tracker.
     * Location revision checks hide old weather immediately, even before cache clearing finishes.
     */
    private fun project(tracker: TrackerData, period: CalendarPeriod) {
        val installations = tracker.installations.sortedWith(compareBy<MeterInstallation> { it.startDate != null }.thenBy { it.startDate })
            .mapIndexed { index, installation -> installation.id to index }.toMap()
        val readings = tracker.readings.sortedWith(compareByDescending<Reading> { it.date }.thenByDescending { installations.getValue(it.installationId) })
        val weatherLocation = temperature?.coordinator?.location?.value
        val projected = TemperatureAggregation.temperaturesByConsumptionDate(
            readings, intervalMeans,
            weatherLocation?.takeIf { it.cacheReady && it.generation == intervalMeansGeneration }?.location,
        )
        state = state.copy(readings = readings, tracker = tracker, period = period,
            dashboard = dashboard(tracker, period), temperatures = projected)
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
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == TrackerViewModel::class.java)
                return modelClass.cast(TrackerViewModel(
                    repository = repository,
                    temperature = temperature,
                    reminderStore = reminderStore,
                    reminderScheduler = reminderScheduler,
                ))!!
            }
        }
    }
}
