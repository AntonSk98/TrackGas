package com.trackgas.app.temperature

import com.trackgas.app.tracking.ReadingRepository
import com.trackgas.app.tracking.CalendarPeriod
import com.trackgas.app.tracking.DateRange
import com.trackgas.app.tracking.PeriodType
import com.trackgas.app.tracking.dashboardDataForPeriod
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/** Requests best-effort backfills and invalidates cached temperatures when the location changes. */
interface TemperatureCheckRequester {
    fun requestCheck()
    fun requestCheck(range: DateRange) = requestCheck()
}

/** Conflates app and connectivity events into best-effort, batched temperature backfills. */
class TemperatureSync(
    private val readings: ReadingRepository,
    private val locationStore: MeterLocationStore,
    private val temperatureStore: TemperatureRepository,
    private val networkMonitor: NetworkMonitor,
    private val provider: TemperatureProvider,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coordinator: TemperatureCoordinator = TemperatureCoordinator(locationStore, temperatureStore),
) : TemperatureCheckRequester {
    private val selectedRange = AtomicReference(
        CalendarPeriod.containing(PeriodType.WEEK, LocalDate.now()).range,
    )
    private val checks = MutableSharedFlow<DateRange>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val networkRegistration = networkMonitor.addOnAvailableListener { requestCheck() }
    private val worker = scope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
        checks.collect { checkNow(it) }
    }

    override fun requestCheck() {
        requestCheck(selectedRange.get())
    }

    override fun requestCheck(range: DateRange) {
        selectedRange.set(range)
        checks.tryEmit(range)
    }

    private suspend fun checkNow(range: DateRange) {
        bestEffort("Temperature check failed") {
            if (!networkMonitor.isOnline()) return@bestEffort
            val captured = coordinator.location.value
            if (!captured.cacheReady) return@bestEffort
            val location = captured.location ?: return@bestEffort
            val tracker = dashboardDataForPeriod(
                readings.observeDashboardData(range).first().tracker,
                range,
            )
            val relevantReadings = tracker.readings.filter { it.date >= range.start }
            val locationFingerprint = location.fingerprint()
            val storedIntervals = temperatureStore.storedForLaterReadings(
                relevantReadings.map { it.id }.toSet(),
                locationFingerprint,
            )
            for (interval in missingIntervals(tracker.readings, storedIntervals, locationFingerprint)) {
                // A failed gap stays missing and is retried by a later check.
                bestEffort("Open-Meteo temperature request failed") {
                    val mean = provider.fetchIntervalTemperatureMean(location, interval)
                    if (mean != null) {
                        coordinator.publish(captured, IntervalTemperatureEntity(
                            interval.earlier.id, interval.later.id, interval.earlier.installationId,
                            interval.start.epochSecond, interval.end.epochSecond, location.fingerprint(), mean.toString(),
                        ))
                    }
                }
            }
            val dailyRanges = missingDailyDateRanges(
                tracker.readings,
                temperatureStore.storedDaily(range, locationFingerprint),
                locationFingerprint,
            ).mapNotNull { missing ->
                val start = maxOf(missing.start, range.start)
                val end = minOf(missing.endInclusive, range.endInclusive)
                if (start <= end) DateRange(start, end) else null
            }
            for (dailyRange in dailyRanges) {
                var start = dailyRange.start
                while (start <= dailyRange.endInclusive) {
                    val endInclusive = minOf(start.plusDays(MAX_DAILY_DAYS_PER_REQUEST - 1), dailyRange.endInclusive)
                    bestEffort("Open-Meteo daily temperature request failed") {
                        val means = provider.fetchDailyMeans(location, start, endInclusive)
                        require(means.keys.all { it in start..endInclusive }) {
                            "Daily temperatures must be within the requested dates"
                        }
                        means.forEach { (date, celsius) ->
                            coordinator.publishDaily(
                                captured,
                                DailyTemperatureEntity(date.toString(), location.fingerprint(), celsius.toString()),
                            )
                        }
                    }
                    start = endInclusive.plusDays(1)
                }
            }
        }
    }

    private suspend fun bestEffort(failureMessage: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            logger.warning(failureMessage)
        }
    }

    fun close() {
        networkRegistration.close()
        worker.cancel()
    }

    private companion object {
        const val MAX_DAILY_DAYS_PER_REQUEST = 31L
        val logger: Logger = Logger.getLogger(TemperatureSync::class.java.name)
    }
}
