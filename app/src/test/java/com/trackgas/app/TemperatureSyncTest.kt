package com.trackgas.app

import com.trackgas.app.temperature.MeterLocation
import com.trackgas.app.temperature.MeterLocationStore
import com.trackgas.app.temperature.NetworkMonitor
import com.trackgas.app.temperature.TemperatureProvider
import com.trackgas.app.temperature.TemperatureRepository
import com.trackgas.app.temperature.TemperatureSync
import com.trackgas.app.tracking.DateRange
import com.trackgas.app.tracking.ReadingRepository
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Verifies that temperature checks batch gaps and remain best-effort across connectivity changes. */
@OptIn(ExperimentalCoroutinesApi::class)
class TemperatureSyncTest {
    private val dispatcher = StandardTestDispatcher()
    private val start = LocalDate.parse("2026-10-01")
    private val location = MeterLocation("Berlin", 52.52, 13.405)

    @Before fun setUp() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    @After fun tearDown() = kotlinx.coroutines.Dispatchers.resetMain()

    @Test
    fun contiguousGapUsesOneRequestAndStoresEveryReturnedDay() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.sync.requestCheck()
        runCurrent()

        assertEquals(listOf(DateRange(start, start.plusDays(9))), fixture.provider.requests)
        assertEquals((0L..9L).map { start.plusDays(it) }.toSet(), fixture.temperatureStore.values.value.keys)
    }

    @Test
    fun requestsOnlyMissingContiguousDates() = runTest(dispatcher) {
        val existing = mapOf(
            start to BigDecimal("4"),
            start.plusDays(1) to BigDecimal("6"),
            start.plusDays(5) to BigDecimal("8"),
            start.plusDays(6) to BigDecimal("10"),
        )
        val fixture = fixture(existing = existing)
        fixture.sync.requestCheck()
        runCurrent()

        assertEquals(
            listOf(
                DateRange(start.plusDays(2), start.plusDays(4)),
                DateRange(start.plusDays(7), start.plusDays(9)),
            ),
            fixture.provider.requests,
        )
        assertEquals(existing.keys + (2L..4L).map { start.plusDays(it) } + (7L..9L).map { start.plusDays(it) },
            fixture.temperatureStore.values.value.keys)
    }

    @Test
    fun offlineCheckMakesNoRequestAndConnectivityReturnRetries() = runTest(dispatcher) {
        val network = FakeNetwork(false)
        val fixture = fixture(network = network)
        fixture.sync.requestCheck()
        runCurrent()
        assertTrue(fixture.provider.requests.isEmpty())

        network.setOnline(true)
        runCurrent()
        assertEquals(1, fixture.provider.requests.size)
    }

    @Test
    fun missingLocationOrTooFewReadingsDoesNotRequestProvider() = runTest(dispatcher) {
        val noLocation = fixture(location = null)
        noLocation.sync.requestCheck()
        runCurrent()
        assertTrue(noLocation.provider.requests.isEmpty())

        val oneReading = fixture(readingCount = 1)
        oneReading.sync.requestCheck()
        runCurrent()
        assertTrue(oneReading.provider.requests.isEmpty())
    }

    @Test
    fun providerFailureLeavesDaysMissingAndDoesNotStopLaterChecks() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.provider.failNext = true
        fixture.sync.requestCheck()
        runCurrent()
        assertTrue(fixture.temperatureStore.values.value.isEmpty())

        fixture.sync.requestCheck()
        runCurrent()
        assertEquals(2, fixture.provider.requests.size)
        assertEquals(10, fixture.temperatureStore.values.value.size)
    }

    @Test
    fun locationRefreshClearsOldValuesAndFetchesTheFullReadingRangeAgain() = runTest(dispatcher) {
        val existing = (0L..9L).associate { start.plusDays(it) to BigDecimal("99") }
        val fixture = fixture(existing = existing)
        val newLocation = MeterLocation("Munich", 48.14, 11.58)
        fixture.locationStore.save(newLocation)

        fixture.sync.requestLocationRefresh()
        runCurrent()

        assertEquals(newLocation, fixture.locationStore.location)
        assertEquals(listOf(newLocation), fixture.provider.locations)
        assertEquals(listOf(DateRange(start, start.plusDays(9))), fixture.provider.requests)
        assertEquals(setOf(BigDecimal("7.5")), fixture.temperatureStore.values.value.values.toSet())
        assertEquals(10, fixture.temperatureStore.values.value.size)
    }

    private fun TestScope.fixture(
        existing: Map<LocalDate, BigDecimal> = emptyMap(),
        location: MeterLocation? = this@TemperatureSyncTest.location,
        network: FakeNetwork = FakeNetwork(true),
        readingCount: Int = 2,
    ): Fixture {
        val temperatureStore = FakeTemperatureRepository(existing)
        val provider = FakeProvider()
        val locationStore = FakeLocationStore(location)
        val sync = TemperatureSync(
            FakeReadingRepository(readingCount, DateRange(start, start.plusDays(9))),
            locationStore,
            temperatureStore,
            network,
            provider,
            backgroundScope,
            dispatcher,
        )
        return Fixture(sync, locationStore, temperatureStore, provider)
    }

    private data class Fixture(
        val sync: TemperatureSync,
        val locationStore: FakeLocationStore,
        val temperatureStore: FakeTemperatureRepository,
        val provider: FakeProvider,
    )

    private class FakeLocationStore(var location: MeterLocation?) : MeterLocationStore {
        override fun read() = location
        override fun save(location: MeterLocation) {
            this.location = location
        }
    }

    private class FakeNetwork(private var online: Boolean) : NetworkMonitor {
        private var listener: (() -> Unit)? = null
        override fun isOnline() = online
        override fun addOnAvailableListener(listener: () -> Unit): AutoCloseable {
            this.listener = listener
            return AutoCloseable { this.listener = null }
        }
        fun setOnline(value: Boolean) {
            online = value
            if (value) listener?.invoke()
        }
    }

    private class FakeReadingRepository(
        private val count: Int,
        private val range: DateRange,
    ) : ReadingRepository {
        override fun observeReadings(): Flow<com.trackgas.app.tracking.CommittedSnapshot> =
            error("Not used by temperature sync")
        override suspend fun readingCount() = count
        override suspend fun readingDateRange() = range
        override suspend fun save(intent: com.trackgas.app.tracking.SaveReadingIntent) = error("Not used")
        override suspend fun delete(id: String) = error("Not used")
        override suspend fun replaceMeter(intent: com.trackgas.app.tracking.ReplaceMeterIntent) = error("Not used")
        override suspend fun purge() = error("Not used")
    }

    private class FakeTemperatureRepository(initial: Map<LocalDate, BigDecimal>) : TemperatureRepository {
        val values = MutableStateFlow(initial)
        override fun observe(): Flow<Map<LocalDate, BigDecimal>> = values
        override suspend fun storedDates() = values.value.keys
        override suspend fun upsert(temperatures: Map<LocalDate, BigDecimal>) {
            values.value = values.value + temperatures
        }
        override suspend fun deleteAll() {
            values.value = emptyMap()
        }
    }

    private class FakeProvider : TemperatureProvider {
        val requests = mutableListOf<DateRange>()
        val locations = mutableListOf<MeterLocation>()
        var failNext = false
        override suspend fun fetch(
            location: MeterLocation,
            start: LocalDate,
            endInclusive: LocalDate,
        ): Map<LocalDate, BigDecimal> {
            locations += location
            requests += DateRange(start, endInclusive)
            if (failNext) {
                failNext = false
                throw IOException("temporary test failure")
            }
            return generateSequence(start) { day ->
                day.plusDays(1).takeIf { it <= endInclusive }
            }.associateWith { BigDecimal("7.5") }
        }
    }
}
