package com.trackgas.app.tracking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

enum class TrackerTab { DASHBOARD, HISTORY }

/**
 * Observable UI snapshot with actual readings in newest-first order and derived consumption.
 *
 * [period] is preserved while switching [tab]; reading mutations refresh [dashboard]
 * without resetting either selection.
 */
data class TrackerState(
    val readings: List<Reading>,
    val period: CalendarPeriod,
    val dashboard: Dashboard,
    val tab: TrackerTab = TrackerTab.DASHBOARD,
)

/**
 * Activity-owned, in-memory source of truth for the gas tracker.
 *
 * UI-thread operations are synchronous and publish a new [state] only after successful
 * validation. Compose observes that state to refresh both tabs. The activity's
 * ViewModel store retains it across configuration changes, but nothing is persisted
 * through process death. A new instance starts empty on Dashboard and the current week.
 *
 * [currentDate] is evaluated for each date-sensitive action. Its default resolves the
 * device timezone at call time; tests can supply a date function or fixed [Clock].
 */
class TrackerViewModel(private val currentDate: () -> LocalDate = { LocalDate.now() }) : ViewModel() {
    /** Uses the supplied clock's date and zone for deterministic callers. */
    constructor(clock: Clock) : this({ LocalDate.now(clock) })

    private val initialPeriod = CalendarPeriod.containing(PeriodType.WEEK, today())
    var state by mutableStateOf(TrackerState(emptyList(), initialPeriod, dashboard(emptyList(), initialPeriod)))
        private set

    fun today(): LocalDate = currentDate()

    fun selectTab(tab: TrackerTab) {
        state = state.copy(tab = tab)
    }

    /** Switching period type selects today's period, rather than reusing a past navigated date. */
    fun selectPeriod(type: PeriodType) = publish(state.readings, CalendarPeriod.containing(type, today()))

    fun navigate(offset: Long) = publish(state.readings, state.period.navigate(offset))

    /**
     * Creates a reading when [id] is null, or replaces the existing reading with that identity.
     *
     * Valid input atomically refreshes history and consumption while preserving the period
     * and tab. Invalid input, including a missing edit target, leaves the snapshot unchanged.
     *
     * @return Field errors; an empty result means the reading was saved.
     */
    fun save(id: String?, date: LocalDate?, input: String, locale: Locale): ReadingErrors {
        if (id != null && state.readings.none { it.id == id }) {
            return ReadingErrors(value = ReadingError.MISSING_READING)
        }
        val result = validateReading(state.readings, id ?: UUID.randomUUID().toString(), date, input, today(), locale)
        val candidate = result.candidate ?: return result.errors
        publish(state.readings.filterNot { it.id == candidate.id } + candidate, state.period)
        return ReadingErrors()
    }

    /**
     * Removes a reading and rebuilds consumption from the remaining consecutive intervals.
     *
     * The UI must obtain confirmation before calling this method. Returns false without
     * modifying state if [id] no longer exists, allowing the UI to surface that failure.
     */
    fun delete(id: String): Boolean {
        if (state.readings.none { it.id == id }) return false
        publish(state.readings.filterNot { it.id == id }, state.period)
        return true
    }

    /** Publishes sorted history and its matching projection together, preserving the active tab. */
    private fun publish(readings: List<Reading>, period: CalendarPeriod) {
        val history = readings.sortedByDescending { it.date }
        state = state.copy(readings = history, period = period, dashboard = dashboard(history, period))
    }
}
