package com.trackgas.app.temperature

import com.trackgas.app.tracking.Reading
import java.time.Instant
import java.time.temporal.ChronoUnit

data class TemperatureInterval(val earlier: Reading, val later: Reading) {
    val start: Instant get() = earlier.observedAt
    val end: Instant get() = later.observedAt
    val firstSample: Instant get() = start.truncatedTo(ChronoUnit.HOURS).let {
        if (it < start) it.plus(1, ChronoUnit.HOURS) else it
    }
    val hasSamples: Boolean get() = firstSample < end
    fun matches(row: IntervalTemperatureEntity): Boolean =
        row.earlierId == earlier.id && row.laterId == later.id &&
            row.installationId == earlier.installationId &&
            row.startEpoch == start.epochSecond && row.endEpoch == end.epochSecond
}

fun readingIntervals(readings: List<Reading>): List<TemperatureInterval> =
    readings.groupBy { it.installationId }.values.flatMap { meter ->
        meter.sortedBy { it.date }.zipWithNext { a, b -> TemperatureInterval(a, b) }
    }

internal fun missingIntervals(readings: List<Reading>, stored: List<IntervalTemperatureEntity>, location: String) =
    readingIntervals(readings).filter { interval ->
        interval.hasSamples && stored.none { it.location == location && interval.matches(it) }
    }

internal fun MeterLocation.fingerprint(): String = "$displayName|$latitude|$longitude"
