package com.trackgas.app.tracking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class TrackerTab { DASHBOARD, HISTORY }
enum class ReadingLoadState { LOADING, READY, ERROR }

data class TrackerState(
    val readings: List<Reading>,
    val period: CalendarPeriod,
    val dashboard: Dashboard,
    val tab: TrackerTab = TrackerTab.DASHBOARD,
    val loadState: ReadingLoadState = ReadingLoadState.LOADING,
    val loadFailure: ReadingFailure? = null,
    val dialog: ReadingDialog = ReadingDialog.Closed,
    val sessionId: String = UUID.randomUUID().toString(),
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

    fun selectPeriod(type: PeriodType) = project(state.readings, CalendarPeriod.containing(type, today()))

    fun navigate(offset: Long) = project(state.readings, state.period.navigate(offset))

    fun retryLoad() {
        if (state.loadState == ReadingLoadState.ERROR) observeReadings()
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

    fun openDelete(reading: Reading) = open(ReadingDialog.ConfirmingDeletion(newId(), reading))

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
                    }
                }
            } catch (error: ReadingStorageException) {
                finishDelete(request, DeletePhase.Failed(ReadingFailure(error.category, ReadingAction.DELETE)))
            }
        }
    }

    private fun finishSave(request: ReadingRequest, phase: SavePhase?) {
        state = state.copy(dialog = state.dialog.completeSave(request, state.sessionId, phase))
    }

    private fun finishDelete(request: ReadingRequest, phase: DeletePhase?) {
        state = state.copy(dialog = state.dialog.completeDelete(request, state.sessionId, phase))
    }

    private fun newId() = UUID.randomUUID().toString()

    private fun acceptCommittedSnapshot(snapshot: CommittedSnapshot) {
        if (snapshot.revision <= acceptedRevision) return
        acceptedRevision = snapshot.revision
        // Reading equality includes BigDecimal scale. Avoid sorting/projecting duplicate data.
        if (snapshot.readings.size == state.readings.size &&
            snapshot.readings.associateBy { it.id } == state.readings.associateBy { it.id }
        ) return
        project(snapshot.readings.sortedByDescending { it.date }, state.period)
    }

    private fun project(readings: List<Reading>, period: CalendarPeriod) {
        state = state.copy(readings = readings, period = period, dashboard = dashboard(readings, period))
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
