package com.trackgas.app

import com.trackgas.app.tracking.*
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class TrackerViewModelTest {
    private fun tracker() = TrackerViewModel(Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC))
    private fun save(vm: TrackerViewModel, date: String, value: String, id: String? = null) {
        assertTrue(vm.save(id, LocalDate.parse(date), value, Locale.US).isEmpty)
    }

    @Test fun stableIdsNewestFirstAtomicErrorsAndPreservedSelection() {
        val vm = tracker()
        vm.selectPeriod(PeriodType.MONTH)
        vm.navigate(-1)
        val selected = vm.state.period
        save(vm, "2026-10-01", "100")
        save(vm, "2026-10-04", "112")
        save(vm, "2026-10-02", "103")
        assertEquals(listOf(4, 2, 1), vm.state.readings.map { it.date.dayOfMonth })
        val original = vm.state
        val middle = original.readings[1]
        assertEquals(ReadingError.ABOVE_NEXT, vm.save(middle.id, middle.date, "120", Locale.US).value)
        assertSame(original, vm.state)
        save(vm, "2026-10-03", "108.125", middle.id)
        assertEquals(middle.id, vm.state.readings[1].id)
        assertEquals(BigDecimal("108.125"), vm.state.readings[1].cubicMetres)
        assertEquals(selected, vm.state.period)
        assertTrue(vm.delete(middle.id))
        assertEquals(selected, vm.state.period)
        assertEquals(2, vm.state.readings.size)
    }

    @Test fun viewSwitchResetsToTodayRatherThanNavigatedPeriod() {
        val vm = tracker()
        vm.navigate(-12)
        vm.selectPeriod(PeriodType.MONTH)
        assertEquals(LocalDate.parse("2026-10-01"), vm.state.period.start)
        vm.navigate(-4)
        vm.selectPeriod(PeriodType.YEAR)
        assertEquals(LocalDate.parse("2026-01-01"), vm.state.period.start)
    }

    @Test fun mutationsReprojectIntervalsAndDeletionEmptiesConsumption() {
        val vm = tracker()
        vm.selectPeriod(PeriodType.MONTH)
        save(vm, "2026-10-01", "1000")
        assertTrue(vm.state.dashboard.buckets.isEmpty())
        val baseline = vm.state.readings.single()
        save(vm, "2026-10-04", "1012")
        val last = vm.state.readings.first()
        assertEquals(BigDecimal("12"), vm.state.dashboard.total.cubicMetres)
        save(vm, "2026-10-02", "1003")
        val middle = vm.state.readings[1]
        assertEquals(0, BigDecimal("3").compareTo(vm.state.dashboard.buckets[0].amount.cubicMetres))
        assertEquals(0, BigDecimal("4.5").compareTo(vm.state.dashboard.buckets[1].amount.cubicMetres))
        save(vm, "2026-10-03", "1008", middle.id)
        assertEquals(0, BigDecimal("4").compareTo(vm.state.dashboard.buckets[0].amount.cubicMetres))
        assertEquals(0, BigDecimal("4").compareTo(vm.state.dashboard.buckets[2].amount.cubicMetres))
        assertTrue(vm.delete(middle.id))
        assertEquals(0, BigDecimal("4").compareTo(vm.state.dashboard.buckets[0].amount.cubicMetres))
        assertEquals(listOf(last, baseline), vm.state.readings)
        val sources = vm.state.readings
        vm.selectPeriod(PeriodType.YEAR)
        assertEquals(sources, vm.state.readings)
        assertTrue(vm.delete(last.id))
        assertEquals(listOf(baseline), vm.state.readings)
        assertNull(vm.state.dashboard.total.cubicMetres)
        assertTrue(vm.state.dashboard.buckets.isEmpty())
        assertTrue(vm.delete(baseline.id))
        assertTrue(vm.state.readings.isEmpty())
        assertNull(vm.state.dashboard.total.cubicMetres)
    }

    @Test fun duplicateDatesAreRejectedWithoutPublishingADifferentSnapshot() {
        val vm = tracker()
        save(vm, "2026-10-26", "100")
        val before = vm.state
        val errors = vm.save(null, LocalDate.parse("2026-10-26"), "101", Locale.US)
        assertEquals(ReadingError.DUPLICATE_DATE, errors.date)
        assertSame(before, vm.state)
        assertEquals(1, vm.state.readings.size)
    }

    @Test fun freshViewModelStartsEmptyOnCurrentWeek() {
        val old = tracker()
        save(old, "2026-10-01", "100")
        old.selectPeriod(PeriodType.YEAR)
        old.selectTab(TrackerTab.HISTORY)
        val fresh = tracker()
        assertTrue(fresh.state.readings.isEmpty())
        assertEquals(CalendarPeriod(PeriodType.WEEK, LocalDate.parse("2026-10-26")), fresh.state.period)
        assertTrue(fresh.state.dashboard.buckets.isEmpty())
        assertNull(fresh.state.dashboard.total.cubicMetres)
        assertEquals(TrackerTab.DASHBOARD, fresh.state.tab)
    }

    @Test fun tabsPreservePeriodAndMutationsPreserveTabWhileRecalculating() {
        val vm = tracker()
        vm.selectPeriod(PeriodType.MONTH)
        vm.navigate(-1)
        val selected = vm.state.period
        val emptyDashboard = vm.state.dashboard
        vm.selectTab(TrackerTab.HISTORY)
        assertEquals(selected, vm.state.period)
        assertSame(emptyDashboard, vm.state.dashboard)
        save(vm, "2026-09-01", "100")
        save(vm, "2026-09-04", "112")
        assertEquals(TrackerTab.HISTORY, vm.state.tab)
        assertEquals(BigDecimal("12"), vm.state.dashboard.total.cubicMetres)
        assertEquals(3, vm.state.dashboard.buckets.size)
        val newest = vm.state.readings.first()
        val beforeInvalidEdit = vm.state
        assertEquals(ReadingError.BELOW_PREVIOUS, vm.save(newest.id, newest.date, "99", Locale.US).value)
        assertSame(beforeInvalidEdit, vm.state)
        save(vm, "2026-09-03", "108", newest.id)
        assertEquals(TrackerTab.HISTORY, vm.state.tab)
        assertEquals(BigDecimal("8"), vm.state.dashboard.total.cubicMetres)
        assertEquals(2, vm.state.dashboard.buckets.size)
        vm.selectTab(TrackerTab.DASHBOARD)
        assertEquals(selected, vm.state.period)
        assertEquals(BigDecimal("8"), vm.state.dashboard.total.cubicMetres)
        vm.selectTab(TrackerTab.HISTORY)
        assertTrue(vm.delete(newest.id))
        assertEquals(TrackerTab.HISTORY, vm.state.tab)
        assertEquals(selected, vm.state.period)
        assertTrue(vm.state.dashboard.buckets.isEmpty())
        vm.selectTab(TrackerTab.DASHBOARD)
        assertEquals(selected, vm.state.period)
        assertEquals(1, vm.state.readings.size)
    }

    @Test fun defaultDateTracksTimezoneChangesWithoutRecreatingViewModel() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            val vm = TrackerViewModel()
            val easternToday = vm.today()
            vm.selectTab(TrackerTab.HISTORY)
            assertTrue(vm.save(null, easternToday.minusDays(2), "100", Locale.US).isEmpty)
            val readings = vm.state.readings

            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(easternToday.minusDays(1), vm.today())
            vm.selectPeriod(PeriodType.WEEK)
            assertEquals(CalendarPeriod.containing(PeriodType.WEEK, vm.today()), vm.state.period)
            assertEquals(TrackerTab.HISTORY, vm.state.tab)
            assertEquals(readings, vm.state.readings)
            val beforeInvalidSave = vm.state
            assertEquals(ReadingError.FUTURE_DATE, vm.save(null, easternToday, "101", Locale.US).date)
            assertSame(beforeInvalidSave, vm.state)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test fun repeatedTabSelectionsNeverResetDataPeriodOrProjection() {
        val vm = tracker()
        vm.selectPeriod(PeriodType.MONTH)
        vm.navigate(-1)
        save(vm, "2026-09-01", "100")
        save(vm, "2026-09-04", "112")
        val period = vm.state.period
        val readings = vm.state.readings
        val projection = vm.state.dashboard
        repeat(10) {
            listOf(TrackerTab.HISTORY, TrackerTab.HISTORY, TrackerTab.DASHBOARD).forEach { tab ->
                vm.selectTab(tab)
                assertEquals(tab, vm.state.tab)
                assertEquals(period, vm.state.period)
                assertEquals(readings, vm.state.readings)
                assertSame(projection, vm.state.dashboard)
            }
        }
    }

    @Test fun injectedDateIsReevaluatedForDefaultsValidationAndTypeSwitching() {
        var date = LocalDate.parse("2026-10-31")
        val vm = TrackerViewModel { date }
        date = LocalDate.parse("2026-11-01")
        assertEquals(date, vm.today())
        vm.selectPeriod(PeriodType.MONTH)
        assertEquals(LocalDate.parse("2026-11-01"), vm.state.period.start)
        assertTrue(vm.save(null, date, "100", Locale.US).isEmpty)
        assertEquals(ReadingError.FUTURE_DATE, vm.save(null, date.plusDays(1), "101", Locale.US).date)
    }
}
