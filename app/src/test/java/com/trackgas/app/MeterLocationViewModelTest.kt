package com.trackgas.app

import com.trackgas.app.temperature.LocationResolver
import com.trackgas.app.temperature.MeterLocation
import com.trackgas.app.temperature.MeterLocationStore
import com.trackgas.app.temperature.NetworkMonitor
import com.trackgas.app.temperature.TemperatureCheckRequester
import com.trackgas.app.temperature.TemperatureRepository
import com.trackgas.app.temperature.TemperatureServices
import com.trackgas.app.tracking.*
import java.io.IOException
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Covers saved meter-location behavior and temperature-driven dashboard refreshes. */
@OptIn(ExperimentalCoroutinesApi::class)
class MeterLocationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.parse("2026-10-26")
    private val saved = MeterLocation("Berlin, Germany", 52.52, 13.405)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun savingSelectedSuggestionPersistsItAndClosesDialog() = runTest(dispatcher) {
        val store = FakeLocationStore()
        val requester = FakeCheckRequester()
        val resolver = FakeLocationResolver { query ->
            assertEquals("Berlin", query)
            listOf(saved)
        }
        val vm = viewModel(store, resolver, FakeNetworkMonitor(true), requester = requester)
        runCurrent()

        vm.openMeterLocation()
        vm.updateMeterLocationQuery("Berlin")
        advanceUntilIdle()
        assertEquals(listOf(saved), editing(vm).suggestions)
        vm.selectMeterLocationSuggestion(saved)
        vm.saveMeterLocation()
        advanceUntilIdle()

        assertEquals(saved, store.location)
        assertEquals(saved, vm.state.meterLocation)
        assertEquals(MeterLocationDialog.Closed, vm.state.locationDialog)
        assertEquals(1, resolver.requests)
        assertEquals(2, requester.requests)
    }

    @Test
    fun typedTextWithoutPickingASuggestionCannotBeSaved() = runTest(dispatcher) {
        val store = FakeLocationStore(saved)
        val vm = viewModel(store, FakeLocationResolver { listOf(saved) }, FakeNetworkMonitor(true))
        runCurrent()

        vm.openMeterLocation()
        assertEquals(saved, editing(vm).selected)
        vm.updateMeterLocationQuery("Berl")
        assertNull(editing(vm).selected)
        vm.saveMeterLocation()
        advanceUntilIdle()

        assertTrue(vm.state.locationDialog is MeterLocationDialog.Editing)
        assertEquals(saved, store.location)
    }

    @Test
    fun locationQueryDropsDigitsButKeepsLettersAndCommas() = runTest(dispatcher) {
        val vm = viewModel(FakeLocationStore(), FakeLocationResolver { emptyList() }, FakeNetworkMonitor(true))
        runCurrent()

        vm.openMeterLocation()
        vm.updateMeterLocationQuery("80331 Bad Gögging, Bavaria 2")

        assertEquals(" Bad Gögging, Bavaria ", editing(vm).query)
    }

    @Test
    fun noMatchAndLookupFailureShowAnErrorAndKeepPreviouslySavedLocation() = runTest(dispatcher) {
        for (result in listOf<() -> List<MeterLocation>>({ emptyList() }, { throw IOException("network unavailable") })) {
            val store = FakeLocationStore(saved)
            val vm = viewModel(store, FakeLocationResolver { result() }, FakeNetworkMonitor(true))
            runCurrent()
            vm.openMeterLocation()
            vm.updateMeterLocationQuery("Different place")
            advanceUntilIdle()

            assertEquals(saved, store.location)
            assertEquals(saved, vm.state.meterLocation)
            assertTrue(editing(vm).lookup is LocationLookupState.Error)
            assertTrue(editing(vm).suggestions.isEmpty())
        }
    }
    @Test
    fun onlineAddPromptsAfterCommitAndLaterAllowsTheNextAddToPromptAgain() = runTest(dispatcher) {
        var date = today
        val repo = MemoryReadingRepository(listOf(reading("old", today.minusDays(1))))
        val requester = FakeCheckRequester()
        val vm = viewModel(
            FakeLocationStore(),
            FakeLocationResolver { listOf(saved) },
            FakeNetworkMonitor(true),
            repo,
            currentDate = { date },
            requester = requester,
        )
        runCurrent()

        saveAdd(vm)
        assertEquals(2, vm.state.readings.size)
        assertTrue((vm.state.locationDialog as MeterLocationDialog.Editing).requestedAfterAdd)
        assertEquals(2, requester.requests)
        vm.dismissMeterLocationDialog()
        assertEquals(MeterLocationDialog.Closed, vm.state.locationDialog)

        date = date.plusDays(1)
        saveAdd(vm)
        assertEquals(3, vm.state.readings.size)
        assertTrue((vm.state.locationDialog as MeterLocationDialog.Editing).requestedAfterAdd)
        assertEquals(3, requester.requests)
    }

    @Test
    fun temperatureEmissionsRefreshStateWithoutResettingPeriodOrTab() = runTest(dispatcher) {
        val temperatures = FakeTemperatureRepository()
        val vm = TrackerViewModel(
            repository = MemoryReadingRepository(listOf(reading("old", today.minusDays(1)), reading("new", today))),
            ioDispatcher = dispatcher,
            temperature = services(store = FakeLocationStore(saved), temperatures = temperatures),
            currentDate = { today },
        )
        runCurrent()
        vm.selectTab(TrackerTab.HISTORY)
        vm.selectPeriod(PeriodType.MONTH)
        vm.navigate(-1)
        val period = vm.state.period
        val temperature = mapOf(today to "6".toBigDecimal())

        val interval = com.trackgas.app.temperature.TemperatureInterval(reading("old", today.minusDays(1)), reading("new", today))
        temperatures.emit(listOf(interval.row(saved, "6")))
        runCurrent()

        assertEquals(temperature, vm.state.temperatures)
        assertEquals(period, vm.state.period)
        assertEquals(TrackerTab.HISTORY, vm.state.tab)
    }

    @Test
    fun savedLocationOrOfflineNetworkSuppressesAddPrompt() = runTest(dispatcher) {
        for ((location, online) in listOf(saved to true, null to false)) {
            val vm = viewModel(FakeLocationStore(location), FakeLocationResolver { listOf(saved) }, FakeNetworkMonitor(online))
            runCurrent()
            saveAdd(vm)
            assertEquals(MeterLocationDialog.Closed, vm.state.locationDialog)
            assertEquals(2, vm.state.readings.size)
        }
    }

    @Test fun locationRoundTripAndBufferedOldEmissionNeverRestoreDeletedMeans() = runTest(dispatcher) {
        val temperatures = FakeTemperatureRepository()
        val readings = listOf(reading("old", today.minusDays(1)), reading("new", today))
        val services = services(store = FakeLocationStore(saved), temperatures = temperatures)
        val vm = TrackerViewModel(MemoryReadingRepository(readings), ioDispatcher = dispatcher,
            temperature = services, currentDate = { today })
        runCurrent()
        val mean = com.trackgas.app.temperature.TemperatureInterval(readings[0], readings[1]).row(saved, "6")
        temperatures.emit(listOf(mean)); runCurrent()
        assertFalse(vm.state.temperatures.isEmpty())
        vm.selectTab(TrackerTab.HISTORY)
        vm.selectPeriod(PeriodType.MONTH)
        val period = vm.state.period
        services.coordinator.save(saved.copy(latitude = 50.0))
        services.coordinator.save(saved)
        runCurrent()
        temperatures.emitStale(listOf(mean)); runCurrent()
        assertTrue(vm.state.temperatures.isEmpty())
        assertEquals(period, vm.state.period)
        assertEquals(TrackerTab.HISTORY, vm.state.tab)
    }

    @Test fun acceptedTimeEditHidesMeansEvenBeforeTemperatureObservationCatchesUp() = runTest(dispatcher) {
        val readings = listOf(reading("old", today.minusDays(1)), reading("new", today))
        val repo = MemoryReadingRepository(readings)
        val temperatures = FakeTemperatureRepository()
        val vm = TrackerViewModel(repo, ioDispatcher = dispatcher,
            temperature = services(store = FakeLocationStore(saved), temperatures = temperatures))
        runCurrent()
        temperatures.emit(listOf(com.trackgas.app.temperature.TemperatureInterval(readings[0], readings[1]).row(saved, "6")))
        runCurrent()
        assertFalse(vm.state.temperatures.isEmpty())
        repo.update(readings.map { it.copy(observedAt = it.observedAt.plusSeconds(60)) })
        runCurrent()
        assertTrue(vm.state.temperatures.isEmpty())
    }

    private fun TestScope.saveAdd(vm: TrackerViewModel) {
        vm.openAdd()
        vm.save(vm.today(), "112", Locale.US)
        runCurrent()
    }

    private fun viewModel(
        store: FakeLocationStore,
        resolver: LocationResolver,
        network: NetworkMonitor,
        repository: MemoryReadingRepository = MemoryReadingRepository(listOf(reading("old", today.minusDays(1)))),
        currentDate: () -> LocalDate = { today },
        requester: FakeCheckRequester = FakeCheckRequester(),
    ) = TrackerViewModel(
        repository = repository,
        ioDispatcher = dispatcher,
        currentDate = currentDate,
        temperature = services(store, resolver, network, requester = requester),
    )

    private fun services(
        store: FakeLocationStore = FakeLocationStore(),
        resolver: LocationResolver = FakeLocationResolver { emptyList() },
        network: NetworkMonitor = FakeNetworkMonitor(true),
        temperatures: TemperatureRepository = FakeTemperatureRepository(),
        requester: FakeCheckRequester = FakeCheckRequester(),
    ) = TemperatureServices(resolver, store, network, temperatures, requester)

    private fun editing(vm: TrackerViewModel) = vm.state.locationDialog as MeterLocationDialog.Editing

    private fun reading(id: String, date: LocalDate) = Reading(id, date, "100".toBigDecimal())

    private class FakeLocationStore(var location: MeterLocation? = null) : MeterLocationStore {
        override fun read() = location
        override fun save(location: MeterLocation) { this.location = location }
    }

    private class FakeLocationResolver(private val result: (String) -> List<MeterLocation>) : LocationResolver {
        var requests = 0
        override fun suggest(query: String): List<MeterLocation> {
            requests++
            return result(query)
        }
    }

    private class FakeNetworkMonitor(private var online: Boolean) : NetworkMonitor {
        override fun isOnline() = online
        override fun addOnAvailableListener(listener: () -> Unit) = AutoCloseable {}
    }

    private class FakeCheckRequester : TemperatureCheckRequester {
        var requests = 0
        var locationRefreshes = 0
        override fun requestCheck() { requests++ }
    }

    private class FakeTemperatureRepository : TemperatureRepository {
        private val state = MutableStateFlow<List<com.trackgas.app.temperature.IntervalTemperatureEntity>>(emptyList())
        private var committed = state.value
        override fun observe() = state
        override suspend fun stored() = committed
        override suspend fun storeIfCurrent(temperature: com.trackgas.app.temperature.IntervalTemperatureEntity): Boolean {
            emit(committed + temperature)
            return true
        }
        override suspend fun deleteAll() {
            emit(emptyList())
        }
        fun emit(temperatures: List<com.trackgas.app.temperature.IntervalTemperatureEntity>) {
            committed = temperatures
            state.value = temperatures
        }
        fun emitStale(temperatures: List<com.trackgas.app.temperature.IntervalTemperatureEntity>) {
            state.value = temperatures
        }
    }

    private class MemoryReadingRepository(readings: List<Reading>) : ReadingRepository {
        private val snapshots = MutableStateFlow(CommittedSnapshot(1, readings))
        private var revision = 1L
        private var latestReadings = readings
        override fun observeReadings(): Flow<CommittedSnapshot> = snapshots
        fun update(readings: List<Reading>) {
            latestReadings = readings
            snapshots.value = CommittedSnapshot(++revision, readings)
        }
        override suspend fun save(intent: SaveReadingIntent): SaveReadingResult {
            val nextDate = when (intent) {
                is SaveReadingIntent.Add -> intent.date ?: error("Test requires a date")
                is SaveReadingIntent.Edit -> error("Test does not edit readings")
            }
            val next = Reading("reading-${revision + 1}", nextDate, (100 + revision).toBigDecimal())
            latestReadings = latestReadings + next
            val snapshot = CommittedSnapshot(++revision, latestReadings)
            snapshots.value = snapshot
            return SaveReadingResult.Saved(snapshot)
        }
        override suspend fun delete(id: String): DeleteReadingResult = error("Not used")
        override suspend fun replaceMeter(intent: ReplaceMeterIntent): ReplaceMeterResult = error("Not used")
        override suspend fun purge(): CommittedSnapshot = error("Not used")
    }
}
