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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemperatureSyncTest {
    private val date = LocalDate.parse("2026-10-01")
    private val range = DateRange(date, date.plusDays(35))
    private val location = MeterLocation("Berlin", 52.52, 13.405)
    private fun rows() = (0L..2L).map { Reading("$it", date.plusDays(it), it.toBigDecimal()) }

    @Test fun backfillReadsOnlySelectedPeriodAndItsBoundaryReadings() = runTest {
        val repo = WeatherReadingRepository(listOf(
            Reading("distant-before", date.minusDays(10), BigDecimal.ZERO),
            Reading("before", date.minusDays(1), BigDecimal.ONE),
            Reading("selected", date, BigDecimal("2")),
            Reading("after", date.plusDays(1), BigDecimal("3")),
            Reading("distant-after", date.plusDays(10), BigDecimal("4")),
        ))
        val store = WeatherMemoryStore(repo)
        val intervalRequests = mutableListOf<DateRange>()
        val dailyRequests = mutableListOf<DateRange>()
        val sync = TemperatureSync(
            repo,
            WeatherLocationStore(location),
            store,
            WeatherNetwork(),
            hourlyProvider(intervalRequests, dailyRequests) { BigDecimal.ONE },
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )

        sync.requestCheck(DateRange(date, date)); runCurrent()

        assertEquals(2, intervalRequests.size)
        assertEquals(listOf(DateRange(date, date)), dailyRequests)
        assertEquals(setOf("selected", "after"), store.values.value.map { it.laterId }.toSet())
        sync.close()
    }

    @Test fun sequentialMissingOnlyRequestsReuseCompletedMeans() = runTest {
        val repo = WeatherReadingRepository(rows())
        val store = WeatherMemoryStore(repo)
        val requests = mutableListOf<DateRange>()
        val dailyRequests = mutableListOf<DateRange>()
        val sync = TemperatureSync(repo, WeatherLocationStore(location), store, WeatherNetwork(),
            hourlyProvider(requests, dailyRequests) { BigDecimal("4") },
            backgroundScope, StandardTestDispatcher(testScheduler))
        sync.requestCheck(range); runCurrent()
        assertEquals(2, requests.size)
        assertEquals(listOf(DateRange(date.plusDays(1), date.plusDays(2))), dailyRequests)
        assertEquals(2, store.values.value.size)
        assertEquals(2, store.dailyValues.value.size)
        sync.requestCheck(range); runCurrent()
        assertEquals(2, requests.size)
        assertEquals(1, dailyRequests.size)
        assertEquals(BigDecimal("4"), BigDecimal(store.values.value.first().celsius))
        sync.close()
    }

    @Test fun newlyCoveredDatesAreFetchedWithoutRefetchingExistingDailyMeans() = runTest {
        val repo = WeatherReadingRepository(rows().take(2))
        val store = WeatherMemoryStore(repo)
        val dailyRequests = mutableListOf<DateRange>()
        val sync = TemperatureSync(
            repo,
            WeatherLocationStore(location),
            store,
            WeatherNetwork(),
            hourlyProvider(dailyRequests = dailyRequests) { BigDecimal.ONE },
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        sync.requestCheck(range); runCurrent()
        assertEquals(listOf(DateRange(date.plusDays(1), date.plusDays(1))), dailyRequests)

        repo.set(rows())
        sync.requestCheck(range); runCurrent()
        assertEquals(
            listOf(
                DateRange(date.plusDays(1), date.plusDays(1)),
                DateRange(date.plusDays(2), date.plusDays(2)),
            ),
            dailyRequests,
        )
        assertEquals(setOf(date.plusDays(1).toString(), date.plusDays(2).toString()), store.dailyValues.value.map { it.date }.toSet())
        sync.close()
    }

    @Test fun noCrossMeterOrEmptyHourPairsAndNoLocationMakeNoRequests() = runTest {
        val short = listOf(
            Reading("a", date, BigDecimal.ZERO, observedAt = date.atTime(23, 45).toInstant(ZoneOffset.UTC)),
            Reading("b", date.plusDays(1), BigDecimal.ONE,
                observedAt = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)),
        )
        val independent = listOf(rows()[0], rows()[1].copy(installationId = "other"))
        val shortRepo = WeatherReadingRepository(short)
        val intervalRequests = mutableListOf<DateRange>()
        val dailyRequests = mutableListOf<DateRange>()
        val shortSync = TemperatureSync(
            shortRepo,
            WeatherLocationStore(location),
            WeatherMemoryStore(shortRepo),
            WeatherNetwork(),
            hourlyProvider(intervalRequests, dailyRequests) { BigDecimal.ONE },
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        shortSync.requestCheck(range); runCurrent()
        assertTrue(intervalRequests.isEmpty())
        assertEquals(listOf(DateRange(date.plusDays(1), date.plusDays(1))), dailyRequests)
        shortSync.close()

        for ((readings, place) in listOf(independent to location, rows() to null)) {
            val repo = WeatherReadingRepository(readings)
            val sync = TemperatureSync(repo, WeatherLocationStore(place), WeatherMemoryStore(repo), WeatherNetwork(),
                hourlyProvider { error("Unexpected request") }, backgroundScope, StandardTestDispatcher(testScheduler))
            sync.requestCheck(range); runCurrent()
            sync.close()
        }
    }

    @Test fun offlineConnectivityReturnAndRestartRetryMissingWeather() = runTest {
        val repo = WeatherReadingRepository(rows().take(2))
        val store = WeatherMemoryStore(repo)
        val network = WeatherNetwork(false)
        val requests = mutableListOf<DateRange>()
        val dailyRequests = mutableListOf<DateRange>()
        val provider = hourlyProvider(requests, dailyRequests) { BigDecimal.ONE }
        var sync = TemperatureSync(repo, WeatherLocationStore(location), store, network, provider,
            backgroundScope, StandardTestDispatcher(testScheduler))
        sync.requestCheck(range); runCurrent()
        assertTrue(requests.isEmpty())
        assertTrue(dailyRequests.isEmpty())
        network.online = true; network.listener?.invoke(); runCurrent()
        assertEquals(1, requests.size)
        assertEquals(1, dailyRequests.size)
        sync.close()
        store.values.value = emptyList()
        sync = TemperatureSync(repo, WeatherLocationStore(location), store, network, provider,
            backgroundScope, StandardTestDispatcher(testScheduler))
        sync.requestCheck(range); runCurrent()
        assertEquals(2, requests.size)
        assertEquals(1, dailyRequests.size)
        sync.close()
    }

    @Test fun unavailableDataRetriesAndCancellationNeverPublishes() = runTest {
        val repo = WeatherReadingRepository(rows().take(2))
        val store = WeatherMemoryStore(repo)
        var available = false
        val sync = TemperatureSync(repo, WeatherLocationStore(location), store, WeatherNetwork(),
            hourlyProvider { if (available) BigDecimal.ONE else null }, backgroundScope, StandardTestDispatcher(testScheduler))
        sync.requestCheck(range); runCurrent()
        assertTrue(store.values.value.isEmpty())
        assertTrue(store.dailyValues.value.isEmpty())
        available = true
        sync.requestCheck(range); runCurrent()
        assertEquals(1, store.values.value.size)
        assertEquals(1, store.dailyValues.value.size)
        sync.close()
        store.values.value = emptyList()
        val suspended = CompletableDeferred<Unit>()
        val waiting = object : TemperatureProvider {
            override suspend fun fetch(location: MeterLocation, start: LocalDate, endInclusive: LocalDate): Map<Instant, BigDecimal> {
                suspended.await()
                return hourlyProvider { BigDecimal.ONE }.fetch(location, start, endInclusive)
            }

            override suspend fun fetchDailyMeans(location: MeterLocation, start: LocalDate, endInclusive: LocalDate) =
                hourlyProvider { BigDecimal.ONE }.fetchDailyMeans(location, start, endInclusive)
        }
        val cancelled = TemperatureSync(repo, WeatherLocationStore(location), store, WeatherNetwork(), waiting,
            backgroundScope, StandardTestDispatcher(testScheduler))
        cancelled.requestCheck(range); runCurrent()
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

                override suspend fun fetchDailyMeans(location: MeterLocation, start: LocalDate, endInclusive: LocalDate) =
                    hourlyProvider { BigDecimal.ONE }.fetchDailyMeans(location, start, endInclusive)
            }
            val sync = TemperatureSync(repo, WeatherLocationStore(location), store, WeatherNetwork(), waiting,
                backgroundScope, StandardTestDispatcher(testScheduler))
            sync.requestCheck(range); runCurrent()
            repo.set(mutation(initial))
            gate.complete(Unit); runCurrent()
            assertTrue(store.values.value.isEmpty())
            sync.close()
        }
    }

    @Test fun obsoleteDailyResponseIsNotStoredAfterItsDateIsNoLongerCovered() = runTest {
        val repo = WeatherReadingRepository(rows().take(2))
        val store = WeatherMemoryStore(repo)
        val gate = CompletableDeferred<Unit>()
        val provider = object : TemperatureProvider {
            override suspend fun fetch(
                location: MeterLocation,
                start: LocalDate,
                endInclusive: LocalDate,
            ) = hourlyProvider { BigDecimal.ONE }.fetch(location, start, endInclusive)

            override suspend fun fetchDailyMeans(
                location: MeterLocation,
                start: LocalDate,
                endInclusive: LocalDate,
            ): Map<LocalDate, BigDecimal> {
                gate.await()
                return mapOf(start to BigDecimal.ONE)
            }
        }
        val sync = TemperatureSync(
            repo, WeatherLocationStore(location), store, WeatherNetwork(), provider,
            backgroundScope, StandardTestDispatcher(testScheduler),
        )
        sync.requestCheck(range); runCurrent()
        repo.set(emptyList())
        gate.complete(Unit); runCurrent()
        assertTrue(store.dailyValues.value.isEmpty())
        sync.close()
    }

    @Test fun dailyRequestsAreBatchedToAtMostThirtyOneDays() = runTest {
        val lastDate = date.plusDays(35)
        val repo = WeatherReadingRepository(listOf(
            Reading("first", date, BigDecimal.ZERO),
            Reading("last", lastDate, BigDecimal.ONE),
        ))
        val dailyRequests = mutableListOf<DateRange>()
        val sync = TemperatureSync(
            repo,
            WeatherLocationStore(location),
            WeatherMemoryStore(repo),
            WeatherNetwork(),
            hourlyProvider(dailyRequests = dailyRequests) { BigDecimal.ONE },
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        sync.requestCheck(range); runCurrent()
        assertEquals(
            listOf(
                DateRange(date.plusDays(1), date.plusDays(31)),
                DateRange(date.plusDays(32), lastDate),
            ),
            dailyRequests,
        )
        sync.close()
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
        assertTrue(TemperatureAggregation.temperaturesByConsumptionDate(repo.current(), store.values.value, restarted.location.value.location).isEmpty())
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
            assertTrue(coordinator.snapshot(TrackerData(repo.current()), range).intervalMeans.isEmpty())
        }
        store.failClear = false
        coordinator.save(location)
        assertTrue(coordinator.location.value.cacheReady)
        assertTrue(coordinator.snapshot(TrackerData(repo.current()), range).intervalMeans.isEmpty())
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

            override suspend fun fetchDailyMeans(location: MeterLocation, start: LocalDate, endInclusive: LocalDate): Map<LocalDate, BigDecimal> {
                places += location
                if (places.size == 1) gate.await()
                return hourlyProvider { BigDecimal.ONE }.fetchDailyMeans(location, start, endInclusive)
            }
        }
        val sync = TemperatureSync(repo, preferences, store, WeatherNetwork(), provider,
            backgroundScope, StandardTestDispatcher(testScheduler), coordinator)
        sync.requestCheck(range); runCurrent()
        val other = location.copy(latitude = 50.0)
        coordinator.save(other)
        gate.complete(Unit); runCurrent()
        assertTrue(store.values.value.isEmpty())
        sync.requestCheck(range); runCurrent()
        assertEquals(listOf(location, location, other, other), places)
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
    val dailyValues = MutableStateFlow<List<DailyTemperatureEntity>>(emptyList())
    var failClear = false
    override fun observeChanges() = merge(values.map { Unit }, dailyValues.map { Unit })
    override suspend fun storedForLaterReadings(readingIds: Set<String>, location: String) =
        values.value.filter { it.laterId in readingIds && it.location == location }
    override suspend fun storedDaily(range: DateRange, location: String) = dailyValues.value.filter {
        it.location == location && LocalDate.parse(it.date) in range.start..range.endInclusive
    }
    override suspend fun pruneObsolete() = Unit
    override suspend fun storeIfCurrent(temperature: IntervalTemperatureEntity): Boolean {
        if (readingIntervals(readings.current()).none { it.matches(temperature) }) return false
        values.value = values.value.filterNot { it.earlierId == temperature.earlierId && it.laterId == temperature.laterId } + temperature
        return true
    }
    override suspend fun storeDailyIfCurrent(temperature: DailyTemperatureEntity): Boolean {
        val date = LocalDate.parse(temperature.date)
        if (readingIntervals(readings.current()).none { date > it.earlier.date && date <= it.later.date }) return false
        dailyValues.value = dailyValues.value.filterNot {
            it.date == temperature.date && it.location == temperature.location
        } + temperature
        return true
    }
    override suspend fun deleteAll() {
        if (failClear) throw IOException()
        values.value = emptyList()
        dailyValues.value = emptyList()
    }
}

internal fun TemperatureInterval.row(location: MeterLocation, celsius: String) = IntervalTemperatureEntity(
    earlier.id, later.id, earlier.installationId, start.epochSecond, end.epochSecond, location.fingerprint(), celsius,
)
