package com.trackgas.app.temperature

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class WeatherLocation(val location: MeterLocation?, val generation: Long, val cacheReady: Boolean = true)

/** Serializes only preference changes and guarded publication, never network requests. */
class TemperatureCoordinator(
    private val store: MeterLocationStore,
    private val temperatures: TemperatureRepository,
) {
    private val mutex = Mutex()
    private val current = MutableStateFlow(WeatherLocation(store.read(), 0))
    val location = current.asStateFlow()
    private var needsClear = false

    suspend fun save(location: MeterLocation) = mutex.withLock {
        withContext(NonCancellable) {
            if (current.value.location != location) {
                store.save(location)
                needsClear = true
                current.value = WeatherLocation(location, current.value.generation + 1, cacheReady = false)
            }
            if (needsClear) {
                temperatures.deleteAll()
                needsClear = false
                current.value = current.value.copy(cacheReady = true)
            }
        }
    }

    suspend fun publish(captured: WeatherLocation, mean: IntervalTemperatureEntity): Boolean = mutex.withLock {
        if (needsClear || current.value != captured || captured.location?.fingerprint() != mean.location) false
        else temperatures.storeIfCurrent(mean)
    }

    suspend fun snapshot(): Pair<WeatherLocation, List<IntervalTemperatureEntity>> = mutex.withLock {
        current.value to if (needsClear) emptyList() else temperatures.stored()
    }
}
