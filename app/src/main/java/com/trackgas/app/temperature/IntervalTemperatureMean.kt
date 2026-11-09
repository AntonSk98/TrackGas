package com.trackgas.app.temperature

import java.math.BigDecimal
import java.math.MathContext
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private const val MAX_DAYS_PER_REQUEST = 31L

/**
 * Fetches hourly weather and calculates one mean for the actual time between two readings.
 * Only sample timestamps in [interval.start, interval.end) count, with equal weight:
 * an 08:15-to-08:15 interval uses 09:00 through the following 08:00, without interpolation.
 *
 * Long intervals are fetched in batches to bound memory use. Samples are discarded after
 * each batch; only their running sum and count survive until the mean is calculated.
 *
 * Returns null if there are no eligible hours or any required sample is missing.
 * The caller must leave the mean absent rather than store a misleading partial average.
 * Provider failures propagate to the background sync, which handles later retries.
 */
internal suspend fun TemperatureProvider.fetchIntervalTemperatureMean(
    location: MeterLocation,
    interval: TemperatureInterval,
): BigDecimal? {
    if (!interval.hasSamples) return null
    var batchStartDate = interval.firstSample.atOffset(ZoneOffset.UTC).toLocalDate()
    // The later reading ends the weather window; an hour exactly at that instant is excluded.
    val lastIncludedHour = interval.end.minusNanos(1).truncatedTo(ChronoUnit.HOURS)
    val lastSampleDate = lastIncludedHour.atOffset(ZoneOffset.UTC).toLocalDate()
    var temperatureSum = BigDecimal.ZERO
    var sampleCount = 0L
    while (batchStartDate <= lastSampleDate) {
        val batchEndDate = minOf(batchStartDate.plusDays(MAX_DAYS_PER_REQUEST - 1), lastSampleDate)
        val samples = fetch(location, batchStartDate, batchEndDate)
        require(samples.keys.all {
            it.epochSecond % 3600L == 0L && it.nano == 0 &&
                it.atOffset(ZoneOffset.UTC).toLocalDate() in batchStartDate..batchEndDate
        }) { "Weather samples must be whole UTC hours within the requested dates" }
        // The API returns whole dates, so trim the first and last batches to the reading times.
        var sampleTime = maxOf(interval.firstSample, batchStartDate.atStartOfDay().toInstant(ZoneOffset.UTC))
        val batchEndExclusive = minOf(interval.end, batchEndDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))
        while (sampleTime < batchEndExclusive) {
            temperatureSum = temperatureSum.add(samples[sampleTime] ?: return null)
            sampleCount++
            sampleTime = sampleTime.plus(1, ChronoUnit.HOURS)
        }
        batchStartDate = batchEndDate.plusDays(1)
    }
    return if (sampleCount == 0L) null
    else temperatureSum.divide(BigDecimal.valueOf(sampleCount), MathContext.DECIMAL128)
}
