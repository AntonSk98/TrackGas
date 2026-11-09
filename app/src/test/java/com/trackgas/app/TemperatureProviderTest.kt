package com.trackgas.app

import com.trackgas.app.temperature.*
import com.trackgas.app.tracking.DateRange
import java.io.IOException
import java.math.BigDecimal
import java.time.*
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TemperatureProviderTest {
    @Test fun requestUsesUtcHourlyEpochsAndRejectsHttpFailuresAndOutOfBoundsSamples() = runTest {
        var url: java.net.URL? = null
        var code = 200
        var body = """{"hourly":{"time":[1793491200],"temperature_2m":[4]}}"""
        var disconnected = false
        val provider = OpenMeteoTemperatureProvider { request ->
            url = request
            object : java.net.HttpURLConnection(request) {
                override fun connect() = Unit
                override fun disconnect() { disconnected = true }
                override fun usingProxy() = false
                override fun getResponseCode() = code
                override fun getInputStream() = body.byteInputStream()
            }
        }
        val date = LocalDate.parse("2026-11-01")
        provider.fetch(location, date, date)
        assertTrue(url.toString().contains("hourly=temperature_2m"))
        assertTrue(url.toString().contains("timezone=UTC"))
        assertTrue(url.toString().contains("timeformat=unixtime"))
        assertTrue(url.toString().contains("start_date=2026-11-01"))
        assertTrue(disconnected)
        code = 503
        try { provider.fetch(location, date, date); fail() } catch (_: IOException) {}
        code = 200
        body = """{"hourly":{"time":[0],"temperature_2m":[4]}}"""
        try { provider.fetch(location, date, date); fail() } catch (_: IllegalArgumentException) {}
        body = """{"hourly":{"time":[0],"temperature_2m":[null]}}"""
        try { provider.fetch(location, date, date); fail() } catch (_: IllegalArgumentException) {}
    }
    private val location = MeterLocation("Berlin", 52.52, 13.405)
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun interval(start: String, end: String): TemperatureInterval {
        fun reading(id: String, text: String) = LocalDateTime.parse(text).atZone(berlin).let {
            Reading(id, it.toLocalDate(), BigDecimal.ZERO, observedAt = it.toInstant(), zone = berlin)
        }
        return TemperatureInterval(reading("a", start), reading("b", end))
    }

    @Test fun parserSkipsNullsAndPreservesPrecision() {
        val result = OpenMeteoTemperatureProvider.parseResponse(
            """{"hourly":{"time":[0,3600,7200],"temperature_2m":[4.12500001,null,8.25]}}""",
        )
        assertEquals(mapOf(Instant.EPOCH to BigDecimal("4.12500001"), Instant.ofEpochSecond(7200) to BigDecimal("8.25")), result)
    }

    @Test fun parserRejectsAlignmentDuplicatesInvalidEpochsAndValues() {
        for (json in listOf(
            """{"hourly":{"time":[0],"temperature_2m":[]}}""",
            """{"hourly":{"time":[0,0],"temperature_2m":[null,4]}}""",
            """{"hourly":{"time":[1],"temperature_2m":[4]}}""",
            """{"hourly":{"time":[0.5],"temperature_2m":[4]}}""",
            """{"hourly":{"time":[0],"temperature_2m":["NaN"]}}""",
            "not json",
        )) assertThrows(Exception::class.java) { OpenMeteoTemperatureProvider.parseResponse(json) }
    }

    @Test fun morningEveningAndFractionalEndpointsSelectHalfOpenWholeHours() = runTest {
        for ((hour, first, last) in listOf(
            Triple("08:00", "2026-11-06T07:00:00Z", "2026-11-07T06:00:00Z"),
            Triple("20:00", "2026-11-06T19:00:00Z", "2026-11-07T18:00:00Z"),
            Triple("08:15", "2026-11-06T08:00:00Z", "2026-11-07T07:00:00Z"),
        )) {
            val interval = interval("2026-11-06T$hour", "2026-11-07T$hour")
            val firstExpected = Instant.parse(first)
            val lastExpected = Instant.parse(last)
            assertEquals(firstExpected, interval.firstSample)
            val provider = hourlyProvider { instant ->
                if (instant in firstExpected..lastExpected)
                    BigDecimal.valueOf(ChronoUnit.HOURS.between(firstExpected, instant) + 1)
                else BigDecimal("1000")
            }
            val mean = provider.fetchIntervalTemperatureMean(location, interval)
            assertEquals(24, ChronoUnit.HOURS.between(firstExpected, lastExpected).toInt() + 1)
            assertEquals(0, BigDecimal("12.5").compareTo(mean))
        }
    }

    @Test fun dstUses23And25AbsoluteSamples() = runTest {
        for ((start, end, count) in listOf(
            Triple("2026-03-28T08:00", "2026-03-29T08:00", 23),
            Triple("2026-10-24T08:00", "2026-10-25T08:00", 25),
        )) {
            val interval = interval(start, end)
            var actual = 0
            val mean = hourlyProvider {
                if (it >= interval.start && it < interval.end) actual++
                BigDecimal("6.125")
            }.fetchIntervalTemperatureMean(location, interval)
            assertEquals(count, actual)
            assertEquals(0, BigDecimal("6.125").compareTo(mean))
        }
    }

    @Test fun missingRequiredSamplePreventsMeanButMissingOutsideDoesNot() = runTest {
        val interval = interval("2026-11-06T08:15", "2026-11-07T08:15")
        val complete = hourlyProvider { if (it >= interval.start && it < interval.end) BigDecimal("4") else null }
        assertEquals(BigDecimal("4"), complete.fetchIntervalTemperatureMean(location, interval))
        val incomplete = hourlyProvider { if (it == interval.firstSample) null else BigDecimal("4") }
        assertNull(incomplete.fetchIntervalTemperatureMean(location, interval))
    }

    @Test fun emptyWindowNeverRequests() = runTest {
        val interval = interval("2026-11-06T23:45", "2026-11-07T00:00")
        val provider = hourlyProvider { error("No request expected") }
        assertFalse(interval.hasSamples)
        assertNull(provider.fetchIntervalTemperatureMean(location, interval))
    }

    @Test fun longIntervalUsesContiguous31UtcDateChunksAndOneMean() = runTest {
        val requests = mutableListOf<DateRange>()
        val interval = interval("2026-01-01T08:15", "2026-03-12T08:15")
        val provider = hourlyProvider(requests) { BigDecimal("7.123456789") }
        assertEquals(0, BigDecimal("7.123456789").compareTo(provider.fetchIntervalTemperatureMean(location, interval)))
        assertEquals(3, requests.size)
        assertTrue(requests.all { it.days <= 31 })
        assertTrue(requests.zipWithNext().all { (a, b) -> a.endInclusive.plusDays(1) == b.start })
    }

    @Test fun laterChunkFailureReturnsNoPartialMean() = runTest {
        var requests = 0
        val provider = hourlyProvider {
            if (++requests > 31 * 24) throw IOException()
            BigDecimal.ONE
        }
        var failed = false
        try { provider.fetchIntervalTemperatureMean(location, interval("2026-01-01T08:00", "2026-03-12T08:00")) }
        catch (_: IOException) { failed = true }
        assertTrue(failed)
    }

    @Test fun midnightEndDoesNotRequestAnEmptyExtraChunk() = runTest {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val end = Instant.parse("2026-02-01T00:00:00Z")
        val interval = TemperatureInterval(
            Reading("a", LocalDate.parse("2026-01-01"), BigDecimal.ZERO, observedAt = start),
            Reading("b", LocalDate.parse("2026-02-01"), BigDecimal.ONE, observedAt = end),
        )
        val requests = mutableListOf<DateRange>()
        assertEquals(BigDecimal.ONE, hourlyProvider(requests) { BigDecimal.ONE }.fetchIntervalTemperatureMean(location, interval))
        assertEquals(listOf(DateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))), requests)
    }
}

internal fun hourlyProvider(
    requests: MutableList<DateRange> = mutableListOf(),
    value: (Instant) -> BigDecimal?,
) = object : TemperatureProvider {
    override suspend fun fetch(location: MeterLocation, start: LocalDate, endInclusive: LocalDate): Map<Instant, BigDecimal> {
        requests += DateRange(start, endInclusive)
        val end = endInclusive.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        return buildMap {
            var hour = start.atStartOfDay().toInstant(ZoneOffset.UTC)
            while (hour < end) {
                value(hour)?.let { put(hour, it) }
                hour = hour.plus(1, ChronoUnit.HOURS)
            }
        }
    }
}
