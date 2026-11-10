package com.trackgas.app

import com.trackgas.app.tracking.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ConsumptionTest {
    @Test fun timeOnlyEditChangesWeatherWindowButNotCalendarConsumption() {
        val original = listOf(reading(1, "100"), reading(4, "112"))
        val changed = original.mapIndexed { index, reading ->
            reading.copy(observedAt = reading.observedAt.plusSeconds((index + 1L) * 6 * 3600))
        }
        val range = DateRange(original.first().date, original.last().date)
        assertEquals(allocate(readingIntervals(original), range), allocate(readingIntervals(changed), range))
        val period = CalendarPeriod(PeriodType.MONTH, original.first().date)
        assertEquals(dashboard(original, period), dashboard(changed, period))
        assertNotEquals(history24HourEstimates(TrackerData(original)), history24HourEstimates(TrackerData(changed)))
        assertNotEquals(
            dailyConsumptionRates(TrackerData(original), period),
            dailyConsumptionRates(TrackerData(changed), period),
        )
        val oldWeather = com.trackgas.app.temperature.readingIntervals(original).single()
        val newWeather = com.trackgas.app.temperature.readingIntervals(changed).single()
        assertNotEquals(oldWeather.start, newWeather.start)
        assertNotEquals(oldWeather.end, newWeather.end)
    }

    private fun reading(day: Int, value: String) =
        Reading("id-$day", LocalDate.of(2026, 10, day), BigDecimal(value))

    private fun observedReading(local: String, value: String, zone: String = "UTC"): Reading {
        val timestamp = LocalDateTime.parse(local).atZone(ZoneId.of(zone))
        return Reading(
            id = local,
            date = timestamp.toLocalDate(),
            cubicMetres = BigDecimal(value),
            observedAt = timestamp.toInstant(),
            zone = timestamp.zone,
        )
    }

    @Test fun dailyRateUsesActualElapsedSecondsWithoutChangingAbsoluteAllocation() {
        val tracker = TrackerData(listOf(
            observedReading("2026-11-06T08:00", "1000"),
            observedReading("2026-11-07T23:00", "1013"),
        ))
        for (type in listOf(PeriodType.WEEK, PeriodType.MONTH)) {
            val period = CalendarPeriod.containing(type, tracker.readings.last().date)
            val before = dashboard(tracker, period)
            val rates = dailyConsumptionRates(tracker, period)
            assertEquals(mapOf(LocalDate.parse("2026-11-07") to BigDecimal("8")), rates)
            assertEquals(BigDecimal("13"), before.total.cubicMetres)
            assertEquals(BigDecimal("13"), before.buckets.single().amount.cubicMetres)
            assertEquals(before, dashboard(tracker, period))
        }
    }

    @Test fun fractionalShortDstAndDifferentZoneIntervalsReuseHistoryNormalization() {
        val examples = listOf(
            Triple(observedReading("2026-11-06T20:00", "100"),
                observedReading("2026-11-07T06:30", "107"), "16"),
            Triple(observedReading("2026-11-06T23:45", "100"),
                observedReading("2026-11-07T00:15", "101"), "48"),
            Triple(observedReading("2026-03-28T08:00", "100", "Europe/Berlin"),
                observedReading("2026-03-29T08:00", "123", "Europe/Berlin"), "24"),
            Triple(observedReading("2026-10-24T08:00", "100", "Europe/Berlin"),
                observedReading("2026-10-25T08:00", "125", "Europe/Berlin"), "24"),
            Triple(observedReading("2026-11-06T08:00", "100", "Europe/Berlin"),
                observedReading("2026-11-07T08:00", "130", "America/New_York"), "24"),
        )
        for ((earlier, later, expected) in examples) {
            val tracker = TrackerData(listOf(later, earlier))
            val period = CalendarPeriod.containing(PeriodType.MONTH, later.date)
            val rates = dailyConsumptionRates(tracker, period)
            assertEquals(setOf(later.date), rates.keys)
            assertEquals(0, BigDecimal(expected).compareTo(rates.getValue(later.date)))
            assertEquals(history24HourEstimates(tracker).getValue(later.id), rates.getValue(later.date))
            assertEquals(later.cubicMetres - earlier.cubicMetres, dashboard(tracker, period).total.cubicMetres)
        }
    }

    @Test fun monthlyRateMeansWeightCoveredDatesRatherThanIntervalsOrCalendarLength() {
        val tracker = TrackerData(listOf(reading(1, "100"), reading(4, "112"), reading(5, "120")))
        val year = CalendarPeriod(PeriodType.YEAR, LocalDate.parse("2026-01-01"))
        val rates = dailyConsumptionRates(tracker, year)
        assertEquals(listOf(2, 3, 4, 5), rates.keys.map { it.dayOfMonth })
        assertEquals(listOf("4", "4", "4", "8").map(::BigDecimal), rates.values.toList())
        val entry = chartProjection(year, dashboard(tracker, year),
            mode = ConsumptionChartMode.DAILY_ESTIMATE, dailyRates = rates).entries.single()
        assertEquals(9, entry.offset)
        assertEquals(BigDecimal("5"), entry.value)
        assertEquals(BigDecimal("20"), dashboard(tracker, year).total.cubicMetres)

        val partial = TrackerData(listOf(reading(10, "100"), reading(12, "112")))
        val partialRates = dailyConsumptionRates(partial, year)
        assertEquals(2, partialRates.size)
        assertEquals(BigDecimal("6"), chartProjection(year, dashboard(partial, year),
            mode = ConsumptionChartMode.DAILY_ESTIMATE, dailyRates = partialRates).entries.single().value)

        val withZero = TrackerData(listOf(reading(10, "100"), reading(11, "100"), reading(12, "108")))
        assertEquals(BigDecimal("4"), chartProjection(year, dashboard(withZero, year),
            mode = ConsumptionChartMode.DAILY_ESTIMATE,
            dailyRates = dailyConsumptionRates(withZero, year)).entries.single().value)
    }

    @Test fun ratesAndMonthlyMeansClipCrossMonthAndYearIntervalsToSelectedDates() {
        val tracker = TrackerData(listOf(
            observedReading("2025-12-30T08:00", "100"),
            observedReading("2026-01-02T08:00", "112"),
            observedReading("2026-02-02T08:00", "360"),
        ))
        val year = CalendarPeriod(PeriodType.YEAR, LocalDate.parse("2026-01-01"))
        val rates = dailyConsumptionRates(tracker, year)
        assertEquals(LocalDate.parse("2026-01-01"), rates.keys.first())
        assertEquals(LocalDate.parse("2026-02-02"), rates.keys.last())
        assertEquals(33, rates.size)
        val entries = chartProjection(year, dashboard(tracker, year),
            mode = ConsumptionChartMode.DAILY_ESTIMATE, dailyRates = rates).entries
        val januaryMean = BigDecimal("240").divide(BigDecimal("31"), java.math.MathContext.DECIMAL128)
        assertEquals(listOf(januaryMean, BigDecimal("8")), entries.map { it.value })
        for ((date, size, expected) in listOf(
            Triple("2025-12-01", 1, "4"),
            Triple("2026-01-01", 31, null),
            Triple("2026-02-01", 2, "8"),
        )) {
            val period = CalendarPeriod(PeriodType.MONTH, LocalDate.parse(date))
            val clipped = dailyConsumptionRates(tracker, period)
            assertEquals(size, clipped.size)
            assertTrue(clipped.keys.all { it in period.range.start..period.range.endInclusive })
            if (expected != null) assertTrue(clipped.values.all { it.compareTo(BigDecimal(expected)) == 0 })
        }
    }

    @Test(timeout = 5000) fun hugeIntervalsEnumerateOnlyTheDisplayedPeriod() {
        val tracker = TrackerData(listOf(
            observedReading("-100000-01-01T08:00", "0"),
            observedReading("+100000-01-01T08:00", "73048500"),
        ))
        for (type in PeriodType.entries) {
            val period = CalendarPeriod.containing(type, LocalDate.parse("2024-02-15"))
            val rates = dailyConsumptionRates(tracker, period)
            assertEquals(period.range.days.toInt(), rates.size)
            assertEquals(period.start, rates.keys.first())
            assertEquals(period.range.endInclusive, rates.keys.last())
            assertTrue(rates.values.all { it == readingIntervals(tracker).single().cubicMetresPer24Hours })
        }
    }

    @Test fun dailyRatesExcludeBaselinesOpeningUsageAndGapsButKeepKnownZero() {
        val period = CalendarPeriod(PeriodType.MONTH, LocalDate.parse("2026-10-01"))
        for (tracker in listOf(TrackerData(), TrackerData(listOf(reading(1, "100"))))) {
            assertTrue(dailyConsumptionRates(tracker, period).isEmpty())
        }
        val original = TrackerData(listOf(reading(1, "100"), reading(2, "100")))
        val rates = dailyConsumptionRates(original, period)
        assertEquals(mapOf(reading(2, "0").date to BigDecimal.ZERO), rates)
        assertNull(rates[reading(1, "0").date])
        assertNull(rates[reading(3, "0").date])

        val replacement = replacementTracker("10", "13")
        val independentRates = dailyConsumptionRates(replacement, period)
        assertEquals(listOf(28, 30), independentRates.keys.map { it.dayOfMonth })
        assertEquals(listOf(BigDecimal("5"), BigDecimal("3")), independentRates.values.toList())
        assertTrue(dailyConsumptionRates(replacement, period.navigate(-1)).isEmpty())
        val openingOnly = replacement.copy(readings = replacement.readings.filter { it.installationId == "new" }.take(1))
        assertEquals(BigDecimal("10"), dashboard(openingOnly, period).total.cubicMetres)
        assertTrue(dailyConsumptionRates(openingOnly, period).isEmpty())

        val oldFinal = reading(29, "1105")
        val mixed = replacement.copy(readings = replacement.readings.filterNot { it.id == "id-28" } + oldFinal)
        val mixedRates = dailyConsumptionRates(mixed, period)
        assertEquals(0, BigDecimal("2.5").compareTo(mixedRates.getValue(oldFinal.date)))
        assertEquals(0, BigDecimal("12.5").compareTo(dashboard(mixed, period).buckets
            .single { it.range.start == oldFinal.date }.amount.cubicMetres))
        assertEquals(0, BigDecimal("3").compareTo(mixedRates.getValue(reading(30, "0").date)))
    }

    @Test fun emptyAndBaselineHaveNoIntervals() {
        assertTrue(readingIntervals(emptyList()).isEmpty())
        assertTrue(readingIntervals(listOf(reading(1, "1000"))).isEmpty())
    }

    @Test fun chronologicalIntervalsRetainSourcesAndExcludeFirstDate() {
        val earlier = reading(1, "1000")
        val later = reading(2, "1004.125")
        val interval = readingIntervals(listOf(later, earlier)).single()
        assertEquals(earlier, interval.earlier)
        assertEquals(later, interval.later)
        assertEquals(later.date, interval.firstCoveredDate)
        assertEquals(later.date, interval.lastCoveredDate)
        assertEquals(1L, interval.days)
        assertEquals(BigDecimal("4.125"), interval.cubicMetres)
        assertFalse(interval.estimatedDistribution)
        assertEquals(BigDecimal.ZERO, readingIntervals(listOf(reading(1, "100"), reading(2, "100"))).single().cubicMetres)
    }

    @Test fun middleReadingReplacesOldIntervalWithoutChangingSources() {
        val readings = listOf(reading(4, "1012"), reading(1, "1000"), reading(2, "1003"))
        val original = readings.toList()
        val intervals = readingIntervals(readings)
        assertEquals(listOf(BigDecimal("3"), BigDecimal("9")), intervals.map { it.cubicMetres })
        assertEquals(readings[2].id, intervals[0].later.id)
        assertEquals(intervals[0].later.id, intervals[1].earlier.id)
        assertEquals(original, readings)
    }

    @Test fun skippedDaysHaveUniformEstimatedAllocationButNoOuterCoverage() {
        val readings = listOf(reading(1, "1000"), reading(4, "1012"))
        val intervals = readingIntervals(readings)
        for (day in 2..4) {
            val result = allocate(intervals, DateRange(reading(day, "0").date, reading(day, "0").date))
            assertEquals(0, BigDecimal("4").compareTo(result.cubicMetres))
            assertEquals(1L, result.coveredDays)
            assertTrue(result.estimatedDistribution)
        }
        for (day in listOf(1, 5)) {
            assertNull(allocate(intervals, DateRange(reading(day, "0").date, reading(day, "0").date)).cubicMetres)
        }
        assertEquals(2, readings.size)
    }

    @Test fun thirdsRetainExactWholeIntervalAndLongGapsNeedNoDailyExpansion() {
        val thirds = readingIntervals(listOf(reading(1, "0"), reading(4, "1")))
        assertEquals(BigDecimal.ONE, allocate(thirds, DateRange(reading(2, "0").date, reading(4, "0").date)).cubicMetres)
        val daily = allocate(thirds, DateRange(reading(2, "0").date, reading(2, "0").date)).cubicMetres!!
        assertEquals("0.333", daily.setScale(3, java.math.RoundingMode.HALF_UP).toPlainString())
        val start = Reading("start", LocalDate.of(1900, 1, 1), BigDecimal.ZERO)
        val end = Reading("end", LocalDate.of(2026, 1, 1), BigDecimal("1000"))
        val result = allocate(readingIntervals(listOf(start, end)), DateRange(start.date.plusDays(1), end.date))
        assertEquals(BigDecimal("1000"), result.cubicMetres)
        assertEquals(java.time.temporal.ChronoUnit.DAYS.between(start.date, end.date), result.coveredDays)
    }

    @Test fun invalidIntervalEndpointsAndSourceCollectionsAreRejected() {
        val first = reading(1, "100")
        val last = reading(4, "112")
        for (invalid in listOf(
            first.copy(id = last.id),
            first.copy(date = last.date, observedAt = last.observedAt),
            first.copy(date = last.date.plusDays(1), observedAt = last.observedAt.plusSeconds(86400)),
            first.copy(cubicMetres = BigDecimal("113")),
        )) {
            assertThrows(IllegalArgumentException::class.java) { ReadingInterval(invalid, last) }
            assertThrows(IllegalArgumentException::class.java) { ReadingInterval(first, last).copy(earlier = invalid) }
        }
        for (invalid in listOf(
            listOf(first, last.copy(id = first.id)),
            listOf(first, last, reading(6, "120").copy(id = first.id)),
            listOf(first, last.copy(date = first.date, observedAt = first.observedAt)),
            listOf(first, last.copy(cubicMetres = BigDecimal("99"))),
        )) {
            assertThrows(IllegalArgumentException::class.java) { readingIntervals(invalid) }
        }
    }

    @Test fun allocationRejectsOverlappingAndUnorderedIntervalsButAllowsUnknownGaps() {
        val first = ReadingInterval(reading(1, "100"), reading(3, "102"))
        val adjacent = ReadingInterval(reading(3, "102"), reading(4, "103"))
        val separated = ReadingInterval(reading(5, "104"), reading(6, "105"))
        val overlap = ReadingInterval(reading(2, "101"), reading(4, "103"))
        val range = DateRange(reading(1, "0").date, reading(6, "0").date)
        for (invalid in listOf(listOf(first, first), listOf(first, overlap), listOf(adjacent, first))) {
            assertThrows(IllegalArgumentException::class.java) { allocate(invalid, range) }
            assertThrows(IllegalArgumentException::class.java) {
                allocate(invalid, DateRange(range.start.minusYears(1), range.endInclusive.minusYears(1)))
            }
        }
        val contiguous = allocate(listOf(first, adjacent), range)
        assertEquals(3L, contiguous.coveredDays)
        assertEquals(BigDecimal("3"), contiguous.cubicMetres)
        val withGap = allocate(listOf(first, separated), range)
        assertEquals(3L, withGap.coveredDays)
        assertEquals(BigDecimal("3"), withGap.cubicMetres)
    }

    @Test fun amountMetadataAndRangesCannotRepresentContradictoryCoverage() {
        val knownZero = ConsumptionAmount(BigDecimal.ZERO, 1, 7, false)
        assertThrows(IllegalArgumentException::class.java) { knownZero.copy(totalDays = 0) }
        assertThrows(IllegalArgumentException::class.java) { knownZero.copy(coveredDays = -1) }
        assertThrows(IllegalArgumentException::class.java) { knownZero.copy(coveredDays = 8) }
        assertThrows(IllegalArgumentException::class.java) { knownZero.copy(cubicMetres = null) }
        assertThrows(IllegalArgumentException::class.java) { knownZero.copy(coveredDays = 0) }
        assertThrows(IllegalArgumentException::class.java) { knownZero.copy(cubicMetres = BigDecimal("-1")) }
        assertThrows(IllegalArgumentException::class.java) { ConsumptionAmount(null, 0, 7, true) }
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-01"))
        }
        assertNull(ConsumptionAmount(null, 0, 7, false).cubicMetres)
    }

    private fun replacementTracker(baseline: String = "0", next: String? = "3"): TrackerData {
        val final = reading(28, "1105")
        val initial = reading(29, baseline).copy(id = "new-first", installationId = "new")
        return TrackerData(
            listOf(reading(27, "1100"), final, initial) +
                listOfNotNull(next?.let { reading(30, it).copy(id = "new-next", installationId = "new") }),
            listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID), MeterInstallation("new", initial.date)),
        )
    }

    @Test fun replacementExampleAttributesFiveAndThreeToTheCorrectDaysWithExactTotalEight() {
        for ((baseline, next) in listOf("0" to "3", "10.0000" to "13.0000", "1200.0000" to "1203.0000")) {
            val source = replacementTracker(baseline, next)
            val shuffled = source.copy(readings = source.readings.reversed(), installations = source.installations.reversed())
            val intervals = readingIntervals(shuffled)
            assertEquals(2, intervals.size)
            assertEquals(listOf(28, 30), intervals.map { it.lastCoveredDate.dayOfMonth })
            assertTrue(intervals.all { it.earlier.installationId == it.later.installationId })
            for ((day, expected) in listOf(28 to "5", 30 to "3")) {
                val date = reading(day, "0").date
                val amount = allocate(intervals, DateRange(date, date))
                assertEquals(0, BigDecimal(expected).compareTo(amount.cubicMetres))
                assertEquals(1L, amount.coveredDays)
                assertFalse(amount.estimatedDistribution)
            }
            val total = allocate(intervals, DateRange(reading(27, "0").date, reading(30, "0").date))
            assertEquals(0, BigDecimal("8").compareTo(total.cubicMetres))
            assertEquals(2L, total.coveredDays)
            assertEquals(readingIntervals(source.readings), intervals)
            assertEquals(source.readings.reversed(), shuffled.readings)
            if (baseline.contains('.')) assertEquals(BigDecimal("3.0000"), intervals.last().cubicMetres)
        }
    }

    @Test fun newBaselineAloneNeverCreatesAnIntervalOrKnownZeroDay() {
        for (baseline in listOf("0", "12.5000")) {
            val intervals = readingIntervals(replacementTracker(baseline, null))
            assertEquals(1, intervals.size)
            assertEquals(BigDecimal("5"), intervals.single().cubicMetres)
            val after = reading(29, "0").date
            assertNull(allocate(intervals, DateRange(after, after)).cubicMetres)
        }
        assertTrue(readingIntervals(TrackerData()).isEmpty())
        assertTrue(readingIntervals(TrackerData(listOf(reading(27, "1100")))).isEmpty())
    }

    @Test fun multipleReplacementsMergeChronologicallyWithoutCrossMeterPairs() {
        val first = replacementTracker("10", "13")
        val secondInitial = reading(30, "100.0000").copy(id = "third-first", installationId = "third")
        val last = reading(31, "104.1250").copy(id = "third-last", installationId = "third")
        val tracker = first.copy(
            readings = (first.readings + secondInitial + last).reversed(),
            installations = first.installations + MeterInstallation("third", secondInitial.date),
        )
        val intervals = readingIntervals(tracker)
        assertEquals(listOf(28, 30, 31), intervals.map { it.lastCoveredDate.dayOfMonth })
        val total = allocate(intervals, DateRange(reading(27, "0").date, last.date))
        assertEquals(BigDecimal("12.1250"), total.cubicMetres)
        assertEquals(3L, total.coveredDays)
        assertFalse(total.estimatedDistribution)
        val finalDay = reading(31, "0").date
        assertEquals(BigDecimal("4.1250"), allocate(intervals, DateRange(finalDay, finalDay)).cubicMetres)
    }

    @Test fun mixedPairsOverlappingInstallationsAndInvalidTrackerStructureAreRejected() {
        val tracker = replacementTracker()
        val final = tracker.readings.single { it.id == "id-28" }
        val initial = tracker.readings.single { it.id == "new-first" }
        assertThrows(IllegalArgumentException::class.java) { ReadingInterval(final, initial) }
        assertThrows(IllegalArgumentException::class.java) {
            ReadingInterval(final, initial.copy(date = final.date.plusDays(1), observedAt = final.observedAt.plusSeconds(86400), cubicMetres = BigDecimal("1200")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            readingIntervals(listOf(reading(27, "1100"), final, initial.copy(date = reading(27, "0").date, observedAt = reading(27, "0").observedAt),
                reading(29, "3").copy(id = "overlap", installationId = "new")))
        }
        for (invalid in listOf(
            tracker.copy(installations = listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID))),
            tracker.copy(readings = tracker.readings + reading(30, "1106")),
        )) {
            assertThrows(IllegalArgumentException::class.java) { readingIntervals(invalid) }
            assertThrows(IllegalArgumentException::class.java) {
                dailyConsumptionRates(invalid, CalendarPeriod(PeriodType.MONTH, LocalDate.of(2026, 1, 1)))
            }
            assertThrows(IllegalArgumentException::class.java) {
                dashboard(invalid, CalendarPeriod(PeriodType.MONTH, LocalDate.of(2026, 1, 1)))
            }
        }
    }
}
