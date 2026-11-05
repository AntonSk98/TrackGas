package com.trackgas.app.temperature

import com.trackgas.app.tracking.ReadingRepository
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** Requests best-effort backfills and invalidates cached temperatures when the location changes. */
interface TemperatureCheckRequester {
    fun requestCheck()

    fun requestLocationRefresh()
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
) : TemperatureCheckRequester {
    private val checks = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val requestedLocationVersion = AtomicLong(0)
    private var clearedLocationVersion = 0L
    private val networkRegistration = networkMonitor.addOnAvailableListener(::requestCheck)
    private val worker = scope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
        checks.collect { checkNow() }
    }

    override fun requestCheck() {
        checks.tryEmit(Unit)
    }

    override fun requestLocationRefresh() {
        requestedLocationVersion.incrementAndGet()
        requestCheck()
    }

    private suspend fun checkNow() {
        bestEffort("Temperature check failed") {
            val locationVersion = requestedLocationVersion.get()
            if (locationVersion > clearedLocationVersion) {
                temperatureStore.deleteAll()
                clearedLocationVersion = locationVersion
            }
            if (!networkMonitor.isOnline()) return@bestEffort
            val location = locationStore.read() ?: return@bestEffort
            if (readings.readingCount() < MINIMUM_READINGS) return@bestEffort
            val range = readings.readingDateRange() ?: return@bestEffort
            for (gap in missingRanges(range, temperatureStore.storedDates())) {
                // A failed gap stays missing and is retried by a later check.
                bestEffort("Open-Meteo temperature request failed") {
                    temperatureStore.upsert(provider.fetch(location, gap.start, gap.endInclusive))
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
        const val MINIMUM_READINGS = 2
        val logger: Logger = Logger.getLogger(TemperatureSync::class.java.name)
    }
}
