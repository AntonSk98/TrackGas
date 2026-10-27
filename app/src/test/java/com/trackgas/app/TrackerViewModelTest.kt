package com.trackgas.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.trackgas.app.tracking.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.parse("2026-10-26")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun tracker(repository: FakeReadingRepository) = TrackerViewModel(repository) { today }

    private fun TestScope.save(vm: TrackerViewModel, date: String, value: String, id: String? = null) {
        vm.save(id, LocalDate.parse(date), value, Locale.US)
        runCurrent()
        assertEquals(ReadingOperation.Saved, vm.state.operation)
        vm.consumeOperation()
    }

    private fun TestScope.delete(vm: TrackerViewModel, id: String) {
        vm.delete(id)
        runCurrent()
        assertEquals(ReadingOperation.Deleted, vm.state.operation)
        vm.consumeOperation()
    }

    @Test fun stableIdsNewestFirstValidationAndPreservedSelection() = runTest {
        val vm = tracker(FakeReadingRepository())
        runCurrent()
        vm.selectPeriod(PeriodType.MONTH)
        vm.navigate(-1)
        vm.selectTab(TrackerTab.HISTORY)
        val selected = vm.state.period
        save(vm, "2026-10-01", "100")
        save(vm, "2026-10-04", "112")
        save(vm, "2026-10-02", "103")
        assertEquals(listOf(4, 2, 1), vm.state.readings.map { it.date.dayOfMonth })
        val before = vm.state
        val middle = before.readings[1]
        vm.save(middle.id, middle.date, "120", Locale.US)
        runCurrent()
        assertEquals(ReadingOperation.Invalid(ReadingErrors(value = ReadingError.ABOVE_NEXT)), vm.state.operation)
        assertEquals(before.readings, vm.state.readings)
        assertEquals(before.dashboard, vm.state.dashboard)
        vm.consumeOperation()
        assertEquals(ReadingOperation.Idle, vm.state.operation)
        save(vm, "2026-10-02", "108.125", middle.id)
        assertEquals(middle.id, vm.state.readings[1].id)
        assertEquals(BigDecimal("108.125"), vm.state.readings[1].cubicMetres)
        assertEquals(selected, vm.state.period)
        assertEquals(TrackerTab.HISTORY, vm.state.tab)
        delete(vm, middle.id)
        assertEquals(selected, vm.state.period)
        assertEquals(TrackerTab.HISTORY, vm.state.tab)
        assertEquals(2, vm.state.readings.size)
    }

    @Test fun periodAndTabNavigationPreservesReadingsAndResetsTypeToToday() = runTest {
        val vm = tracker(FakeReadingRepository())
        runCurrent()
        vm.navigate(-12)
        vm.selectPeriod(PeriodType.MONTH)
        assertEquals(LocalDate.parse("2026-10-01"), vm.state.period.start)
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
                assertEquals(projection, vm.state.dashboard)
            }
        }
        vm.navigate(-4)
        vm.selectPeriod(PeriodType.YEAR)
        assertEquals(LocalDate.parse("2026-01-01"), vm.state.period.start)
        assertEquals(readings, vm.state.readings)
    }

    @Test fun mutationsReprojectIntervalsAndDeletionEmptiesConsumption() = runTest {
        val vm = tracker(FakeReadingRepository())
        runCurrent()
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
        save(vm, "2026-10-02", "1008", middle.id)
        assertEquals(0, BigDecimal("8").compareTo(vm.state.dashboard.buckets[0].amount.cubicMetres))
        assertEquals(0, BigDecimal("2").compareTo(vm.state.dashboard.buckets[2].amount.cubicMetres))
        delete(vm, middle.id)
        assertEquals(0, BigDecimal("4").compareTo(vm.state.dashboard.buckets[0].amount.cubicMetres))
        assertEquals(listOf(last, baseline), vm.state.readings)
        vm.selectPeriod(PeriodType.YEAR)
        delete(vm, last.id)
        assertEquals(listOf(baseline), vm.state.readings)
        assertNull(vm.state.dashboard.total.cubicMetres)
        assertTrue(vm.state.dashboard.buckets.isEmpty())
        delete(vm, baseline.id)
        assertTrue(vm.state.readings.isEmpty())
        assertNull(vm.state.dashboard.total.cubicMetres)
    }

    @Test fun duplicateDateAndChronologicalValidationDoNotChangeCommittedSnapshot() = runTest {
        val vm = tracker(FakeReadingRepository())
        runCurrent()
        save(vm, "2026-10-01", "100")
        save(vm, "2026-10-26", "110")
        val before = vm.state
        vm.save(null, today, "111", Locale.US)
        runCurrent()
        assertEquals(ReadingOperation.Invalid(ReadingErrors(date = ReadingError.DUPLICATE_DATE)), vm.state.operation)
        assertEquals(before.readings, vm.state.readings)
        assertEquals(before.dashboard, vm.state.dashboard)
        vm.consumeOperation()
        vm.save(before.readings.first().id, today, "99", Locale.US)
        runCurrent()
        assertEquals(ReadingOperation.Invalid(ReadingErrors(value = ReadingError.BELOW_PREVIOUS)), vm.state.operation)
        assertEquals(before.readings, vm.state.readings)
        assertEquals(before.dashboard, vm.state.dashboard)
    }

    @Test fun loadingBlocksMutationsUntilStoredSnapshotArrives() = runTest {
        val repository = FakeReadingRepository(listOf(reading("one", "2026-10-01", "100")))
        val gate = CompletableDeferred<Unit>()
        repository.loadGate = gate
        val vm = tracker(repository)
        runCurrent()
        assertEquals(ReadingLoadState.LOADING, vm.state.loadState)
        vm.save(null, today, "101", Locale.US)
        vm.delete("one")
        runCurrent()
        assertEquals(0, repository.saveCalls)
        assertEquals(0, repository.deleteCalls)
        gate.complete(Unit)
        runCurrent()
        assertEquals(ReadingLoadState.READY, vm.state.loadState)
        assertEquals(listOf("one"), vm.state.readings.map { it.id })
    }

    @Test fun loadFailureCanBeRetriedWithoutDiscardingStoredReadings() = runTest {
        val repository = FakeReadingRepository(listOf(reading("one", "2026-10-01", "100")))
        repository.failLoad = true
        val vm = tracker(repository)
        runCurrent()
        assertEquals(ReadingLoadState.ERROR, vm.state.loadState)
        vm.save(null, today, "101", Locale.US)
        runCurrent()
        assertEquals(0, repository.saveCalls)
        repository.failLoad = false
        vm.retryLoad()
        runCurrent()
        assertEquals(ReadingLoadState.READY, vm.state.loadState)
        assertEquals(listOf("one"), vm.state.readings.map { it.id })
        assertEquals(2, repository.loadCalls)
    }

    @Test fun failedSaveAndDeleteKeepCommittedProjectionAndCanBeRetried() = runTest {
        val repository = FakeReadingRepository()
        val vm = tracker(repository)
        runCurrent()
        vm.selectPeriod(PeriodType.MONTH)
        save(vm, "2026-10-01", "100")
        save(vm, "2026-10-04", "112")
        val before = vm.state
        val target = before.readings.first()
        repository.failSave = true
        vm.save(target.id, target.date, "115.25", Locale.US)
        runCurrent()
        assertEquals(ReadingOperation.Failed, vm.state.operation)
        assertEquals(before.readings, vm.state.readings)
        assertEquals(before.dashboard, vm.state.dashboard)
        vm.consumeOperation()
        repository.failSave = false
        save(vm, "2026-10-04", "115.25", target.id)
        assertEquals(target.id, vm.state.readings.first().id)
        assertEquals(BigDecimal("115.25"), vm.state.readings.first().cubicMetres)
        val edited = vm.state
        repository.failDelete = true
        vm.delete(target.id)
        runCurrent()
        assertEquals(ReadingOperation.Failed, vm.state.operation)
        assertEquals(edited.readings, vm.state.readings)
        assertEquals(edited.dashboard, vm.state.dashboard)
        vm.consumeOperation()
        repository.failDelete = false
        delete(vm, target.id)
        assertEquals(listOf(before.readings.last()), vm.state.readings)
        assertNull(vm.state.dashboard.total.cubicMetres)
    }

    @Test fun failedAddCanBeRetriedWithoutLosingCommittedData() = runTest {
        val repository = FakeReadingRepository(listOf(reading("one", "2026-10-01", "100")))
        val vm = tracker(repository)
        runCurrent()
        val before = vm.state
        repository.failSave = true
        vm.save(null, today, "110.1250", Locale.US)
        runCurrent()
        assertEquals(ReadingOperation.Failed, vm.state.operation)
        assertEquals(before.readings, vm.state.readings)
        assertEquals(before.dashboard, vm.state.dashboard)
        assertEquals(before.readings, repository.snapshot)
        vm.consumeOperation()
        repository.failSave = false
        save(vm, "2026-10-26", "110.1250")
        assertEquals(BigDecimal("110.1250"), vm.state.readings.first().cubicMetres)
        assertEquals(2, vm.state.readings.size)
    }

    @Test fun pendingSaveRejectsOverlappingSaveAndDeleteUntilCommit() = runTest {
        val repository = FakeReadingRepository(listOf(reading("one", "2026-10-01", "100")))
        val vm = tracker(repository)
        runCurrent()
        val before = vm.state
        val gate = CompletableDeferred<Unit>()
        repository.writeGate = gate
        vm.save(null, today, "110", Locale.US)
        runCurrent()
        assertEquals(ReadingOperation.Pending, vm.state.operation)
        assertEquals(before.readings, vm.state.readings)
        assertEquals(before.dashboard, vm.state.dashboard)
        vm.save(null, today, "110", Locale.US)
        vm.delete("one")
        runCurrent()
        assertEquals(1, repository.saveCalls)
        assertEquals(0, repository.deleteCalls)
        gate.complete(Unit)
        runCurrent()
        assertEquals(ReadingOperation.Saved, vm.state.operation)
        assertEquals(2, vm.state.readings.size)
        assertEquals(2, repository.snapshot.size)
    }

    @Test fun pendingDeleteRejectsOverlappingMutationsUntilCommit() = runTest {
        val repository = FakeReadingRepository(listOf(reading("one", "2026-10-01", "100")))
        val vm = tracker(repository)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.writeGate = gate
        vm.delete("one")
        runCurrent()
        assertEquals(ReadingOperation.Pending, vm.state.operation)
        assertEquals(1, vm.state.readings.size)
        vm.delete("one")
        vm.save(null, today, "110", Locale.US)
        runCurrent()
        assertEquals(1, repository.deleteCalls)
        assertEquals(0, repository.saveCalls)
        gate.complete(Unit)
        runCurrent()
        assertEquals(ReadingOperation.Deleted, vm.state.operation)
        assertTrue(vm.state.readings.isEmpty())
    }

    @Test fun missingEditAndDeleteReportErrorsWithoutChangingSnapshot() = runTest {
        val repository = FakeReadingRepository(listOf(reading("one", "2026-10-01", "100")))
        val vm = tracker(repository)
        runCurrent()
        val before = vm.state
        vm.save("missing", today, "110", Locale.US)
        runCurrent()
        assertEquals(
            ReadingOperation.Invalid(ReadingErrors(value = ReadingError.MISSING_READING)),
            vm.state.operation,
        )
        assertEquals(before.readings, vm.state.readings)
        assertEquals(before.dashboard, vm.state.dashboard)
        vm.consumeOperation()
        vm.delete("missing")
        runCurrent()
        assertEquals(ReadingOperation.Missing, vm.state.operation)
        assertEquals(before.readings, vm.state.readings)
        assertEquals(before.dashboard, vm.state.dashboard)
    }

    @Test fun freshViewModelRestoresCommittedEditsAndDeletionsWithDefaultNavigation() = runTest {
        val repository = FakeReadingRepository()
        val old = tracker(repository)
        runCurrent()
        save(old, "2026-10-20", "100.0001")
        save(old, "2026-10-23", "112.1250")
        save(old, "2026-10-25", "120")
        val edited = old.state.readings[1]
        save(old, "2026-10-23", "113.1250", edited.id)
        delete(old, old.state.readings.first().id)
        old.selectPeriod(PeriodType.YEAR)
        old.selectTab(TrackerTab.HISTORY)
        val fresh = tracker(repository)
        runCurrent()
        assertEquals(ReadingLoadState.READY, fresh.state.loadState)
        assertEquals(old.state.readings, fresh.state.readings)
        assertEquals(edited.id, fresh.state.readings.first().id)
        assertEquals(BigDecimal("113.1250"), fresh.state.readings.first().cubicMetres)
        assertEquals(CalendarPeriod(PeriodType.WEEK, today), fresh.state.period)
        assertEquals(TrackerTab.DASHBOARD, fresh.state.tab)
        assertEquals(dashboard(fresh.state.readings, fresh.state.period), fresh.state.dashboard)
    }

    @Test fun activityRecreationRetainsViewModelAndSelectedState() = runTest {
        val repository = FakeReadingRepository(listOf(reading("one", "2026-10-01", "100")))
        val store = ViewModelStore()
        try {
            val factory = TrackerViewModel.factory(repository)
            val original = ViewModelProvider(store, factory)[TrackerViewModel::class.java]
            runCurrent()
            original.selectTab(TrackerTab.HISTORY)
            original.selectPeriod(PeriodType.MONTH)
            original.navigate(-1)
            val before = original.state

            val recreated = ViewModelProvider(store, factory)[TrackerViewModel::class.java]
            assertSame(original, recreated)
            assertEquals(before, recreated.state)
            assertEquals(1, repository.loadCalls)
        } finally {
            store.clear()
        }
    }

    @Test fun separateViewModelsObserveEachOthersCommittedChanges() = runTest {
        val repository = FakeReadingRepository()
        val first = tracker(repository)
        val second = tracker(repository)
        runCurrent()
        save(first, "2026-10-25", "100")
        save(first, "2026-10-26", "110")
        runCurrent()
        assertEquals(first.state.readings, second.state.readings)
        assertEquals(BigDecimal("10"), second.state.dashboard.total.cubicMetres)

        delete(second, second.state.readings.first().id)
        runCurrent()
        assertEquals(second.state.readings, first.state.readings)
        assertNull(first.state.dashboard.total.cubicMetres)
    }

    @Test fun injectedDateIsReevaluatedForValidationAndPeriodSelection() = runTest {
        var date = LocalDate.parse("2026-10-31")
        val vm = TrackerViewModel(FakeReadingRepository()) { date }
        runCurrent()
        date = LocalDate.parse("2026-11-01")
        assertEquals(date, vm.today())
        vm.selectPeriod(PeriodType.MONTH)
        assertEquals(date, vm.state.period.start)
        save(vm, "2026-11-01", "100")
        vm.save(null, date.plusDays(1), "101", Locale.US)
        runCurrent()
        assertEquals(ReadingOperation.Invalid(ReadingErrors(date = ReadingError.FUTURE_DATE)), vm.state.operation)
    }

    @Test fun defaultDateTracksTimezoneChangesWithoutRecreatingViewModel() = runTest {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            val vm = TrackerViewModel(FakeReadingRepository())
            runCurrent()
            val easternToday = vm.today()
            vm.selectTab(TrackerTab.HISTORY)
            save(vm, easternToday.minusDays(2).toString(), "100")
            val readings = vm.state.readings
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(easternToday.minusDays(1), vm.today())
            vm.selectPeriod(PeriodType.WEEK)
            assertEquals(CalendarPeriod.containing(PeriodType.WEEK, vm.today()), vm.state.period)
            assertEquals(TrackerTab.HISTORY, vm.state.tab)
            assertEquals(readings, vm.state.readings)
            vm.save(null, easternToday, "101", Locale.US)
            runCurrent()
            assertEquals(ReadingOperation.Invalid(ReadingErrors(date = ReadingError.FUTURE_DATE)), vm.state.operation)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun reading(id: String, date: String, value: String) = Reading(id, LocalDate.parse(date), BigDecimal(value))
}

private class FakeReadingRepository(initial: List<Reading> = emptyList()) : ReadingRepository {
    private val readings = MutableStateFlow(initial.sortedByDescending { it.date })
    private var nextId = 0
    var loadGate: CompletableDeferred<Unit>? = null
    var writeGate: CompletableDeferred<Unit>? = null
    var failLoad = false
    var failSave = false
    var failDelete = false
    var loadCalls = 0
    var saveCalls = 0
    var deleteCalls = 0
    val snapshot: List<Reading> get() = readings.value

    override fun observeReadings(): Flow<List<Reading>> = flow {
        loadCalls++
        loadGate?.await()
        if (failLoad) throw ReadingStorageException(IllegalStateException("load failed"))
        emitAll(readings)
    }

    override suspend fun save(id: String?, date: LocalDate?, input: String, today: LocalDate, locale: Locale): SaveReadingResult {
        saveCalls++
        writeGate?.await()
        if (failSave) throw ReadingStorageException(IllegalStateException("save failed"))
        val existing = id?.let { target -> snapshot.firstOrNull { it.id == target } }
        if (id != null && existing == null) {
            return SaveReadingResult.Invalid(ReadingErrors(value = ReadingError.MISSING_READING))
        }
        val newId = id ?: generateSequence { "fake-${++nextId}" }.first { candidate -> snapshot.none { it.id == candidate } }
        val validation = validateReading(snapshot, newId, existing?.date ?: date, input, today, locale)
        val candidate = validation.candidate ?: return SaveReadingResult.Invalid(validation.errors)
        readings.value = (snapshot.filterNot { it.id == candidate.id } + candidate).sortedByDescending { it.date }
        return SaveReadingResult.Saved(snapshot)
    }

    override suspend fun delete(id: String): DeleteReadingResult {
        deleteCalls++
        writeGate?.await()
        if (failDelete) throw ReadingStorageException(IllegalStateException("delete failed"))
        if (snapshot.none { it.id == id }) return DeleteReadingResult.Missing
        readings.value = snapshot.filterNot { it.id == id }
        return DeleteReadingResult.Deleted(snapshot)
    }
}
