package com.trackgas.app.temperature

import com.trackgas.app.tracking.ReadingRepository
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
    private val checks = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val networkRegistration = networkMonitor.addOnAvailableListener(::requestCheck)
    private val worker = scope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
        checks.collect { checkNow() }
    }

    override fun requestCheck() {
        checks.tryEmit(Unit)
    }

    private suspend fun checkNow() {
        bestEffort("Temperature check failed") {
            if (!networkMonitor.isOnline()) return@bestEffort
            val captured = coordinator.location.value
            if (!captured.cacheReady) return@bestEffort
            val location = captured.location ?: return@bestEffort
            val tracker = readings.observeReadings().first().tracker
            for (interval in missingIntervals(tracker.readings, temperatureStore.stored(), location.fingerprint())) {
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
        val logger: Logger = Logger.getLogger(TemperatureSync::class.java.name)
    }
}
