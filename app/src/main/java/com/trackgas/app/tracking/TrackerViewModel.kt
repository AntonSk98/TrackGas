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

sealed interface ReadingOperation {
    data object Idle : ReadingOperation
    data object Pending : ReadingOperation
    data object Saved : ReadingOperation
    data object Deleted : ReadingOperation
    data class Invalid(val errors: ReadingErrors) : ReadingOperation
    data object Missing : ReadingOperation
    data object Failed : ReadingOperation
}

data class TrackerState(
    val readings: List<Reading>,
    val period: CalendarPeriod,
    val dashboard: Dashboard,
    val tab: TrackerTab = TrackerTab.DASHBOARD,
    val loadState: ReadingLoadState = ReadingLoadState.LOADING,
    val operation: ReadingOperation = ReadingOperation.Idle,
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

    init {
        observeReadings(showLoading = true)
    }

    fun today(): LocalDate = currentDate()

    fun selectTab(tab: TrackerTab) {
        state = state.copy(tab = tab)
    }

    fun selectPeriod(type: PeriodType) = publish(state.readings, CalendarPeriod.containing(type, today()))

    fun navigate(offset: Long) = publish(state.readings, state.period.navigate(offset))

    fun retryLoad() {
        if (state.loadState == ReadingLoadState.ERROR) observeReadings(showLoading = true)
    }

    private fun observeReadings(showLoading: Boolean) {
        observation?.cancel()
        if (showLoading) state = state.copy(loadState = ReadingLoadState.LOADING)
        observation = viewModelScope.launch {
            try {
                repository.observeReadings().collect { readings ->
                    publish(readings, state.period)
                    state = state.copy(loadState = ReadingLoadState.READY)
                }
            } catch (error: ReadingStorageException) {
                state = state.copy(loadState = ReadingLoadState.ERROR)
            }
        }
    }

    fun consumeOperation() {
        if (state.operation != ReadingOperation.Pending) {
            state = state.copy(operation = ReadingOperation.Idle)
        }
    }

    fun save(id: String?, date: LocalDate?, input: String, locale: Locale) {
        if (state.loadState != ReadingLoadState.READY || state.operation == ReadingOperation.Pending) return
        state = state.copy(operation = ReadingOperation.Pending)
        viewModelScope.launch {
            try {
                when (val result = repository.save(id, date, input, today(), locale)) {
                    is SaveReadingResult.Invalid -> state = state.copy(operation = ReadingOperation.Invalid(result.errors))
                    is SaveReadingResult.Saved -> {
                        publish(result.readings, state.period)
                        observeReadings(showLoading = false)
                        state = state.copy(operation = ReadingOperation.Saved)
                    }
                }
            } catch (error: ReadingStorageException) {
                state = state.copy(operation = ReadingOperation.Failed)
            }
        }
    }

    fun delete(id: String) {
        if (state.loadState != ReadingLoadState.READY || state.operation == ReadingOperation.Pending) return
        state = state.copy(operation = ReadingOperation.Pending)
        viewModelScope.launch {
            try {
                when (val result = repository.delete(id)) {
                    DeleteReadingResult.Missing -> state = state.copy(operation = ReadingOperation.Missing)
                    is DeleteReadingResult.Deleted -> {
                        publish(result.readings, state.period)
                        observeReadings(showLoading = false)
                        state = state.copy(operation = ReadingOperation.Deleted)
                    }
                }
            } catch (error: ReadingStorageException) {
                state = state.copy(operation = ReadingOperation.Failed)
            }
        }
    }

    private fun publish(readings: List<Reading>, period: CalendarPeriod) {
        val history = readings.sortedByDescending { it.date }
        state = state.copy(readings = history, period = period, dashboard = dashboard(history, period))
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
