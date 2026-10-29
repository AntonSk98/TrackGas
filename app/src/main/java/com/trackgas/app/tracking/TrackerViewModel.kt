package com.trackgas.app.tracking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TrackerTab { DASHBOARD, HISTORY }
enum class ReadingLoadState { LOADING, READY, ERROR }

sealed interface ExportState {
    data object Idle : ExportState
    data class Selecting(val requestId: String, val tracker: TrackerData) : ExportState
    data class Writing(val requestId: String, val tracker: TrackerData) : ExportState
    data class Complete(val requestId: String, val succeeded: Boolean) : ExportState
}

data class TrackerState(
    val readings: List<Reading>,
    val period: CalendarPeriod,
    val dashboard: Dashboard,
    val tab: TrackerTab = TrackerTab.DASHBOARD,
    val loadState: ReadingLoadState = ReadingLoadState.LOADING,
    val loadFailure: ReadingFailure? = null,
    val dialog: ReadingDialog = ReadingDialog.Closed,
    val sessionId: String = UUID.randomUUID().toString(),
    val tracker: TrackerData = TrackerData(readings),
    val export: ExportState = ExportState.Idle,
)

/** Publishes projections only from committed repository snapshots. */
class TrackerViewModel(
    private val repository: ReadingRepository,
    private val currentDate: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val initialPeriod = CalendarPeriod.containing(PeriodType.WEEK, today())
    var state by mutableStateOf(TrackerState(emptyList(), initialPeriod, dashboard(emptyList(), initialPeriod)))
        private set
    private var observation: Job? = null
    private var observationGeneration = 0L
    private var acceptedRevision = Long.MIN_VALUE

    init {
        observeReadings()
    }

    fun today(): LocalDate = currentDate()

    fun selectTab(tab: TrackerTab) {
        state = state.copy(tab = tab)
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
            replacements = tracker.replacements.toList(),
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
            val succeeded = withContext(Dispatchers.IO) { writeReadingCsv(snapshot, open) }
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

    fun openAdd() = open(ReadingDialog.Editing(newId(), ReadingTarget.Add(today())))

    fun openEdit(reading: Reading) = open(ReadingDialog.Editing(newId(), ReadingTarget.Edit(reading)))

    fun openDelete(reading: Reading) = open(ReadingDialog.ConfirmingDeletion(
        newId(), reading, if (state.tracker.isBoundaryReading(reading.id)) DeletePhase.Protected else DeletePhase.Ready,
    ))

    fun openPurge() = open(ReadingDialog.ConfirmingPurge(newId()))

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
                    }
                }
            } catch (error: ReadingStorageException) {
                finishReplacement(request, ReplacementPhase.Failed(ReadingFailure(error.category, ReadingAction.REPLACE)))
            }
        }
    }

    fun save(date: LocalDate?, input: String, locale: Locale) {
        val dialog = state.dialog as? ReadingDialog.Editing ?: return
        if (state.loadState != ReadingLoadState.READY || dialog.pending) return
        val request = ReadingRequest(state.sessionId, dialog.dialogId, newId())
        val meter = parseMeter(input, locale)
        val intent = when (val target = dialog.target) {
            is ReadingTarget.Add -> SaveReadingIntent.Add(date, meter, today())
            is ReadingTarget.Edit -> SaveReadingIntent.Edit(target.reading.id, meter, today())
        }
        state = state.copy(dialog = dialog.copy(phase = SavePhase.Pending(request.requestId)))
        viewModelScope.launch {
            try {
                when (val result = repository.save(intent)) {
                    is SaveReadingResult.Invalid -> finishSave(request, SavePhase.Invalid(result.errors))
                    is SaveReadingResult.Saved -> {
                        acceptCommittedSnapshot(result.snapshot)
                        finishSave(request, null)
                    }
                }
            } catch (error: ReadingStorageException) {
                finishSave(request, SavePhase.Failed(ReadingFailure(error.category, ReadingAction.SAVE)))
            }
        }
    }

    fun delete() {
        val dialog = state.dialog as? ReadingDialog.ConfirmingDeletion ?: return
        if (state.loadState != ReadingLoadState.READY || dialog.pending || dialog.phase == DeletePhase.Protected) return
        val request = ReadingRequest(state.sessionId, dialog.dialogId, newId())
        state = state.copy(dialog = dialog.copy(phase = DeletePhase.Pending(request.requestId)))
        viewModelScope.launch {
            try {
                when (val result = repository.delete(dialog.reading.id)) {
                    DeleteReadingResult.Missing -> finishDelete(request, DeletePhase.Missing)
                    DeleteReadingResult.Protected -> finishDelete(request, DeletePhase.Protected)
                    is DeleteReadingResult.Deleted -> {
                        acceptCommittedSnapshot(result.snapshot)
                        finishDelete(request, null)
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
                finishPurge(request, null)
            } catch (error: ReadingStorageException) {
                finishPurge(request, PurgePhase.Failed(ReadingFailure(error.category, ReadingAction.PURGE)))
            }
        }
    }

    private fun finishPurge(request: ReadingRequest, phase: PurgePhase?) {
        state = state.copy(dialog = state.dialog.completePurge(request, state.sessionId, phase))
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

    private fun acceptCommittedSnapshot(snapshot: CommittedSnapshot) {
        if (snapshot.revision <= acceptedRevision) return
        acceptedRevision = snapshot.revision
        // All three collections matter; reading equality includes exact decimal scale.
        if (snapshot.tracker.readings.size == state.tracker.readings.size &&
            snapshot.tracker.installations.size == state.tracker.installations.size &&
            snapshot.tracker.replacements.size == state.tracker.replacements.size &&
            snapshot.readings.associateBy { it.id } == state.tracker.readings.associateBy { it.id } &&
            snapshot.tracker.installations.associateBy { it.id } == state.tracker.installations.associateBy { it.id } &&
            snapshot.tracker.replacements.associateBy { it.id } == state.tracker.replacements.associateBy { it.id }
        ) return
        project(snapshot.tracker, state.period)
    }

    private fun project(tracker: TrackerData, period: CalendarPeriod) {
        val installations = tracker.installations.sortedWith(compareBy<MeterInstallation> { it.startDate != null }.thenBy { it.startDate })
            .mapIndexed { index, installation -> installation.id to index }.toMap()
        val readings = tracker.readings.sortedWith(compareByDescending<Reading> { it.date }.thenByDescending { installations.getValue(it.installationId) })
        state = state.copy(readings = readings, tracker = tracker, period = period, dashboard = dashboard(tracker, period))
    }

    companion object {
        fun factory(repository: ReadingRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == TrackerViewModel::class.java)
                return modelClass.cast(TrackerViewModel(repository))!!
            }
        }
    }
}
