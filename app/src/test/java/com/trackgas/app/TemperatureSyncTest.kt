package com.trackgas.app

import com.trackgas.app.temperature.*
import com.trackgas.app.temperature.readingIntervals
import com.trackgas.app.tracking.*
import java.io.IOException
import java.math.BigDecimal
import java.time.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemperatureSyncTest {
    private val date = LocalDate.parse("2026-10-01")
    private val location = MeterLocation("Berlin", 52.52, 13.405)
    private fun rows() = (0L..2L).map { Reading("$it", date.plusDays(it), it.toBigDecimal()) }

    @Test fun sequentialMissingOnlyRequestsReuseCompletedMeans() = runTest {
        val repo = WeatherReadingRepository(rows())
        val store = WeatherMemoryStore(repo)
        val requests = mutableListOf<DateRange>()
        val sync = TemperatureSync(repo, WeatherLocationStore(location), store, WeatherNetwork(),
            hourlyProvider(requests) { BigDecimal("4") }, backgroundScope, StandardTestDispatcher(testScheduler))
        sync.requestCheck(); runCurrent()
        assertEquals(2, requests.size)
        assertEquals(2, store.values.value.size)
        sync.requestCheck(); runCurrent()
        assertEquals(2, requests.size)
        assertEquals(BigDecimal("4"), BigDecimal(store.values.value.first().celsius))
        sync.close()
    }

    @Test fun noCrossMeterOrEmptyHourPairsAndNoLocationMakeNoRequests() = runTest {
        val short = listOf(
            Reading("a", date, BigDecimal.ZERO, observedAt = date.atTime(23, 45).toInstant(ZoneOffset.UTC)),
            Reading("b", date.plusDays(1), BigDecimal.ONE,
                observedAt = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)),
        )
        val independent = listOf(rows()[0], rows()[1].copy(installationId = "other"))
        for ((readings, place) in listOf(short to location, independent to location, rows() to null)) {
            val repo = WeatherReadingRepository(readings)
            val sync = TemperatureSync(repo, WeatherLocationStore(place), WeatherMemoryStore(repo), WeatherNetwork(),
                hourlyProvider { error("Unexpected request") }, backgroundScope, StandardTestDispatcher(testScheduler))
            sync.requestCheck(); runCurrent()
            sync.close()
        }
    }

    @Test fun offlineConnectivityReturnAndRestartRetryMissingWeather() = runTest {
        val repo = WeatherReadingRepository(rows().take(2))
        val store = WeatherMemoryStore(repo)
        val network = WeatherNetwork(false)
        val requests = mutableListOf<DateRange>()
        val provider = hourlyProvider(requests) { BigDecimal.ONE }
        var sync = TemperatureSync(repo, WeatherLocationStore(location), store, network, provider,
            backgroundScope, StandardTestDispatcher(testScheduler))
        sync.requestCheck(); runCurrent()
        assertTrue(requests.isEmpty())
        network.online = true; network.listener?.invoke(); runCurrent()
        assertEquals(1, requests.size)
        sync.close()
        store.values.value = emptyList()
        sync = TemperatureSync(repo, WeatherLocationStore(location), store, network, provider,
            backgroundScope, StandardTestDispatcher(testScheduler))
        sync.requestCheck(); runCurrent()
        assertEquals(2, requests.size)
        sync.close()
    }

    @Test fun unavailableDataRetriesAndCancellationNeverPublishes() = runTest {
        val repo = WeatherReadingRepository(rows().take(2))
        val store = WeatherMemoryStore(repo)
        var available = false
        val sync = TemperatureSync(repo, WeatherLocationStore(location), store, WeatherNetwork(),
            hourlyProvider { if (available) BigDecimal.ONE else null }, backgroundScope, StandardTestDispatcher(testScheduler))
        sync.requestCheck(); runCurrent()
        assertTrue(store.values.value.isEmpty())
        available = true
        sync.requestCheck(); runCurrent()
        assertEquals(1, store.values.value.size)
        sync.close()
        store.values.value = emptyList()
        val suspended = CompletableDeferred<Unit>()
        val waiting = object : TemperatureProvider {
            override suspend fun fetch(location: MeterLocation, start: LocalDate, endInclusive: LocalDate): Map<Instant, BigDecimal> {
                suspended.await()
                return hourlyProvider { BigDecimal.ONE }.fetch(location, start, endInclusive)
            }
        }
        val cancelled = TemperatureSync(repo, WeatherLocationStore(location), store, WeatherNetwork(), waiting,
            backgroundScope, StandardTestDispatcher(testScheduler))
        cancelled.requestCheck(); runCurrent()
        cancelled.close(); suspended.complete(Unit); runCurrent()
        assertTrue(store.values.value.isEmpty())
    }

    @Test fun mutationsDuringHttpCannotRestoreObsoleteResults() = runTest {
        for (mutation in listOf<(List<com.trackgas.app.tracking.Reading>) -> List<com.trackgas.app.tracking.Reading>>(
            { it.map { r -> if (r.id == "0") r.copy(observedAt = r.observedAt.plusSeconds(60)) else r } },
            { it + Reading("middle", date.plusDays(1), BigDecimal.ONE) },
            { it.drop(1) },
            { it.map { r -> r.copy(id = "import-${r.id}") } },
            { emptyList() },
        )) {
            val initial = listOf(rows()[0], rows()[2])
            val repo = WeatherReadingRepository(initial)
            val store = WeatherMemoryStore(repo)
            val gate = CompletableDeferred<Unit>()
            val waiting = object : TemperatureProvider {
                override suspend fun fetch(location: MeterLocation, start: LocalDate, endInclusive: LocalDate): Map<Instant, BigDecimal> {
                    gate.await()
                    return hourlyProvider { BigDecimal.ONE }.fetch(location, start, endInclusive)
                }
            }
            val sync = TemperatureSync(repo, WeatherLocationStore(location), store, WeatherNetwork(), waiting,
                backgroundScope, StandardTestDispatcher(testScheduler))
            sync.requestCheck(); runCurrent()
            repo.set(mutation(initial))
            gate.complete(Unit); runCurrent()
            assertTrue(store.values.value.isEmpty())
            sync.close()
        }
    }

    @Test fun locationGenerationRejectsAtoBtoAAndRestartFiltersOldProvenance() = runTest {
        val repo = WeatherReadingRepository(rows().take(2))
        val store = WeatherMemoryStore(repo)
        val preferences = WeatherLocationStore(location)
        val coordinator = TemperatureCoordinator(preferences, store)
        val captured = coordinator.location.value
        val interval = readingIntervals(repo.current()).single()
        val mean = interval.row(location, "4")
        val other = location.copy(displayName = "Other")
        coordinator.save(other)
        coordinator.save(location)
        assertFalse(coordinator.publish(captured, mean))
        assertTrue(store.values.value.isEmpty())
        store.values.value = listOf(mean)
        preferences.location = other
        val restarted = TemperatureCoordinator(preferences, store)
        assertTrue(TemperatureAggregation.temperaturesByConsumptionDate(repo.current(), store.stored(), restarted.location.value.location).isEmpty())
    }

    @Test fun saveAndClearFailuresAreVisibleAndCannotPublishUntilClearSucceeds() = runTest {
        val repo = WeatherReadingRepository(rows().take(2))
        val store = WeatherMemoryStore(repo)
        val preferences = WeatherLocationStore(location)
        val coordinator = TemperatureCoordinator(preferences, store)
        val other = location.copy(displayName = "Other")
        preferences.failSave = true
        try { coordinator.save(other); fail() } catch (_: IOException) {}
        assertEquals(location, coordinator.location.value.location)
        preferences.failSave = false
        store.failClear = true
        try { coordinator.save(other); fail() } catch (_: IOException) {}
        assertEquals(other, coordinator.location.value.location)
        assertFalse(coordinator.location.value.cacheReady)
        val mean = readingIntervals(repo.current()).single().row(other, "4")
        assertFalse(coordinator.publish(coordinator.location.value, mean))
        store.failClear = false
        coordinator.save(other)
        assertTrue(coordinator.location.value.cacheReady)
        assertTrue(coordinator.publish(coordinator.location.value, mean))
    }

    @Test fun failedAtoBtoAClearKeepsEvenMatchingOldRowsHidden() = runTest {
        val repo = WeatherReadingRepository(rows().take(2))
        val store = WeatherMemoryStore(repo)
        val coordinator = TemperatureCoordinator(WeatherLocationStore(location), store)
        store.values.value = listOf(readingIntervals(repo.current()).single().row(location, "4"))
        store.failClear = true
        for (place in listOf(location.copy(displayName = "Other"), location)) {
            try { coordinator.save(place); fail() } catch (_: IOException) {}
            assertFalse(coordinator.location.value.cacheReady)
            assertTrue(coordinator.snapshot().second.isEmpty())
        }
        store.failClear = false
        coordinator.save(location)
        assertTrue(coordinator.location.value.cacheReady)
        assertTrue(coordinator.snapshot().second.isEmpty())
    }

    @Test fun locationChangeDuringHttpRejectsTheOldResponseAndRefreshesOnlyNewLocation() = runTest {
        val repo = WeatherReadingRepository(rows().take(2))
        val store = WeatherMemoryStore(repo)
        val preferences = WeatherLocationStore(location)
        val coordinator = TemperatureCoordinator(preferences, store)
        val gate = CompletableDeferred<Unit>()
        val places = mutableListOf<MeterLocation>()
        val provider = object : TemperatureProvider {
            override suspend fun fetch(location: MeterLocation, start: LocalDate, endInclusive: LocalDate): Map<Instant, BigDecimal> {
                places += location
                if (places.size == 1) gate.await()
                return hourlyProvider { BigDecimal.ONE }.fetch(location, start, endInclusive)
            }
        }
        val sync = TemperatureSync(repo, preferences, store, WeatherNetwork(), provider,
            backgroundScope, StandardTestDispatcher(testScheduler), coordinator)
        sync.requestCheck(); runCurrent()
        val other = location.copy(latitude = 50.0)
        coordinator.save(other)
        gate.complete(Unit); runCurrent()
        assertTrue(store.values.value.isEmpty())
        sync.requestCheck(); runCurrent()
        assertEquals(listOf(location, other), places)
        assertEquals(other.fingerprint(), store.values.value.single().location)
        sync.close()
    }

    @Test fun unchangedInputsSurviveNotesAndValueEditsWhileSplitAndMergeAreMissing() {
        val initial = rows()
        val means = readingIntervals(initial).map { it.row(location, "4") }
        val edited = initial.map { it.copy(note = "note", cubicMetres = it.cubicMetres.add(BigDecimal.ONE)) }
        assertTrue(missingIntervals(edited, means, location.fingerprint()).isEmpty())
        val merged = listOf(initial.first(), initial.last())
        assertEquals(1, missingIntervals(merged, means, location.fingerprint()).size)
        assertEquals(2, missingIntervals(initial, listOf(readingIntervals(merged).single().row(location, "4")), location.fingerprint()).size)
    }
}

internal class WeatherLocationStore(var location: MeterLocation?) : MeterLocationStore {
    var failSave = false
    override fun read() = location
    override fun save(location: MeterLocation) {
        if (failSave) throw IOException()
        this.location = location
    }
}

internal class WeatherNetwork(var online: Boolean = true) : NetworkMonitor {
    var listener: (() -> Unit)? = null
    override fun isOnline() = online
    override fun addOnAvailableListener(listener: () -> Unit): AutoCloseable {
        this.listener = listener
        return AutoCloseable { this.listener = null }
    }
}

internal class WeatherReadingRepository(readings: List<com.trackgas.app.tracking.Reading>) : ReadingRepository {
    private val snapshots = MutableStateFlow(CommittedSnapshot(1, readings))
    fun current() = snapshots.value.readings
    fun set(readings: List<com.trackgas.app.tracking.Reading>) {
        snapshots.value = CommittedSnapshot(snapshots.value.revision + 1, readings)
    }
    override fun observeReadings() = snapshots
    override suspend fun save(intent: SaveReadingIntent): SaveReadingResult = error("Not used")
    override suspend fun delete(id: String): DeleteReadingResult = error("Not used")
    override suspend fun replaceMeter(intent: ReplaceMeterIntent): ReplaceMeterResult = error("Not used")
    override suspend fun purge(): CommittedSnapshot = error("Not used")
}

internal class WeatherMemoryStore(private val readings: WeatherReadingRepository) : TemperatureRepository {
    val values = MutableStateFlow<List<IntervalTemperatureEntity>>(emptyList())
    var failClear = false
    override fun observe() = values
    override suspend fun stored() = values.value
    override suspend fun storeIfCurrent(temperature: IntervalTemperatureEntity): Boolean {
        if (readingIntervals(readings.current()).none { it.matches(temperature) }) return false
        values.value = values.value.filterNot { it.earlierId == temperature.earlierId && it.laterId == temperature.laterId } + temperature
        return true
    }
    override suspend fun deleteAll() {
        if (failClear) throw IOException()
        values.value = emptyList()
    }
}

internal fun TemperatureInterval.row(location: MeterLocation, celsius: String) = IntervalTemperatureEntity(
    earlier.id, later.id, earlier.installationId, start.epochSecond, end.epochSecond, location.fingerprint(), celsius,
)
