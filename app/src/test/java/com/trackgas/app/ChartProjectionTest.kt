package com.trackgas.app

import com.trackgas.app.tracking.CalendarPeriod
import com.trackgas.app.tracking.ConsumptionAmount
import com.trackgas.app.tracking.ConsumptionBucket
import com.trackgas.app.tracking.ConsumptionChartMode
import com.trackgas.app.tracking.Dashboard
import com.trackgas.app.tracking.DateRange
import com.trackgas.app.tracking.PeriodType
import com.trackgas.app.tracking.Reading
import com.trackgas.app.tracking.MeterInstallation
import com.trackgas.app.tracking.ORIGINAL_INSTALLATION_ID
import com.trackgas.app.tracking.TrackerData
import com.trackgas.app.temperature.TemperatureAggregation
import com.trackgas.app.tracking.chartAmountText
import com.trackgas.app.tracking.chartAxisText
import com.trackgas.app.tracking.chartProjection
import com.trackgas.app.tracking.temperatureSegments
import com.trackgas.app.tracking.temperatureOnly
import com.trackgas.app.tracking.TemperatureChartEntry
import com.trackgas.app.tracking.dashboard
import com.trackgas.app.tracking.dailyConsumptionRates
import com.trackgas.app.tracking.weeklyRows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ChartProjectionTest {
    private val october = CalendarPeriod(PeriodType.MONTH, LocalDate.parse("2026-10-01"))
    private val year = CalendarPeriod(PeriodType.YEAR, LocalDate.parse("2026-01-01"))

    private fun reading(date: String, value: String) = Reading(date, LocalDate.parse(date), BigDecimal(value))

    private fun bucket(date: String, value: String, estimated: Boolean = false): ConsumptionBucket {
        val day = LocalDate.parse(date)
        return ConsumptionBucket(DateRange(day, day), ConsumptionAmount(BigDecimal(value), 1, 1, estimated))
    }

    private fun snapshot(vararg buckets: ConsumptionBucket): Dashboard = Dashboard(
        ConsumptionAmount(BigDecimal("987.654321"), buckets.size.toLong(), 31, false),
        buckets.toList(),
    )

    @Test fun chartModeIsPartOfSnapshotIdentityEvenWhenEveryValueIsEqual() {
        val tracker = TrackerData(listOf(reading("2026-10-01", "100"), reading("2026-10-02", "104")))
        val data = dashboard(tracker, october)
        val absolute = chartProjection(october, data)
        val daily = chartProjection(october, data, mode = ConsumptionChartMode.DAILY_ESTIMATE,
            dailyRates = dailyConsumptionRates(tracker, october))
        assertEquals(ConsumptionChartMode.ABSOLUTE, absolute.mode)
        assertEquals(ConsumptionChartMode.DAILY_ESTIMATE, daily.mode)
        assertEquals(absolute.entries, daily.entries)
        assertEquals(absolute.scale, daily.scale)
        assertEquals(absolute.temperatures, daily.temperatures)
        assertNotEquals(absolute, daily)
        assertEquals(absolute, daily.copy(mode = ConsumptionChartMode.ABSOLUTE))
    }

    @Test fun temperatureSnapshotIgnoresGasModeValuesAndScaleButTracksWeatherAndPeriod() {
        val data = snapshot(bucket("2026-10-02", "13"))
        val temperatures = mapOf(LocalDate.parse("2026-10-02") to BigDecimal("8"))
        val absolute = chartProjection(october, data, temperatures)
        val daily = chartProjection(october, data, temperatures, ConsumptionChartMode.DAILY_ESTIMATE,
            mapOf(LocalDate.parse("2026-10-02") to BigDecimal("4")))
        val temperature = absolute.temperatureOnly()
        assertNotEquals(absolute, daily)
        assertEquals(temperature, daily.temperatureOnly())
        assertEquals(temperature, daily.copy(entries = emptyList(), scale = BigDecimal("99")).temperatureOnly())
        assertEquals(absolute.temperatures, temperature.temperatures)
        assertTrue(temperature.entries.isEmpty())
        assertEquals(BigDecimal.ZERO, temperature.scale)
        assertNotEquals(temperature, chartProjection(october, data,
            mapOf(LocalDate.parse("2026-10-02") to BigDecimal("9"))).temperatureOnly())
        assertNotEquals(temperature, temperature.copy(period = october.navigate(1)))
    }

    @Test fun dailyValuesChangeChartOnlyAndRetainAbsoluteRowsTotalsAndTemperaturePoints() {
        val tracker = TrackerData(listOf(
            reading("2026-10-01", "100"),
            reading("2026-10-02", "113").copy(
                observedAt = reading("2026-10-02", "113").observedAt.plusSeconds(15 * 3600)),
            reading("2026-10-04", "118.5"),
        ))
        val temperatures = mapOf(
            LocalDate.parse("2026-10-02") to BigDecimal("8"),
            LocalDate.parse("2026-10-03") to BigDecimal("10"),
            LocalDate.parse("2026-10-04") to BigDecimal("12"),
        )
        for (type in PeriodType.entries) {
            val period = CalendarPeriod.containing(type, LocalDate.parse("2026-10-02"))
            val data = dashboard(tracker, period)
            val before = data.copy(buckets = data.buckets.toList())
            val absolute = chartProjection(period, data, temperatures)
            val rates = dailyConsumptionRates(tracker, period)
            val daily = chartProjection(period, data, temperatures, ConsumptionChartMode.DAILY_ESTIMATE, rates)
            assertEquals(before, data)
            assertEquals(before, dashboard(tracker, period))
            assertEquals(absolute.temperatures, daily.temperatures)
            assertEquals(absolute.entries.map { it.offset }, daily.entries.map { it.offset })
            assertEquals(absolute.positionCount, daily.positionCount)
            if (type == PeriodType.YEAR) {
                val expected = TemperatureAggregation.mean(period, rates)!!
                assertEquals(expected, daily.entries.single().value)
                assertEquals(BigDecimal("18.5"), absolute.entries.single().value)
                assertEquals(BigDecimal("10"), daily.temperatures.single().celsius)
            } else {
                assertEquals(BigDecimal("8"), daily.entries.first().value)
                assertEquals(BigDecimal("13"), absolute.entries.first().value)
                assertEquals(BigDecimal("8"), daily.scale)
                assertEquals(BigDecimal("13"), absolute.scale)
                assertEquals(BigDecimal("4.0"), daily.amountAt(0.5))
            }
        }
    }

    @Test fun yearlyRateMeansExcludeMissingDatesAndUseFullCalendarMonthPositions() {
        val data = snapshot(bucket("2026-01-01", "100"), bucket("2026-03-01", "200"))
        val rates = mapOf(
            LocalDate.parse("2025-12-31") to BigDecimal("1000"),
            LocalDate.parse("2026-01-30") to BigDecimal("4"),
            LocalDate.parse("2026-01-31") to BigDecimal("8"),
            LocalDate.parse("2026-03-01") to BigDecimal.ZERO,
            LocalDate.parse("2027-01-01") to BigDecimal("1000"),
        )
        val projection = chartProjection(year, data, mode = ConsumptionChartMode.DAILY_ESTIMATE, dailyRates = rates)
        assertEquals(12, projection.positionCount)
        assertEquals(listOf(0, 2), projection.entries.map { it.offset })
        assertEquals(listOf(BigDecimal("6"), BigDecimal.ZERO), projection.entries.map { it.value })
        assertEquals(DateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")),
            projection.entries.first().range)
        assertEquals(DateRange(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31")),
            projection.entries.last().range)
        assertTrue(projection.entries.last().isZero)
    }

    @Test fun dailyZeroMissingAndExtremeValuesKeepExistingGraphicsAndDecimalPolicies() {
        val dates = listOf("2026-10-02", "2026-10-03", "2026-10-31").map(LocalDate::parse)
        val values = listOf(BigDecimal("0.000"), BigDecimal("1E-1000"), BigDecimal("1E+1000"))
        val rates = dates.zip(values).toMap()
        val data = snapshot(*dates.map { bucket(it.toString(), "1") }.toTypedArray())
        val projected = chartProjection(october, data, mode = ConsumptionChartMode.DAILY_ESTIMATE, dailyRates = rates)
        assertEquals(listOf(1, 2, 30), projected.entries.map { it.offset })
        assertEquals(values, projected.entries.map { it.value })
        assertTrue(projected.entries.first().isZero)
        assertEquals(0.0, projected.entries.first().normalizedY, 0.0)
        assertFalse(projected.entries[1].isZero)
        assertEquals(Double.MIN_VALUE, projected.entries[1].normalizedY, 0.0)
        assertEquals(1.0, projected.entries.last().normalizedY, 0.0)
        values.zip(projected.entries).forEach { (value, entry) -> assertSame(value, entry.value) }
        assertEquals(0, projected.scale.compareTo(projected.amountAt(1.0)))

        val zeroOnly = chartProjection(october, data, mode = ConsumptionChartMode.DAILY_ESTIMATE,
            dailyRates = mapOf(dates.first() to BigDecimal("0.000")))
        assertTrue(zeroOnly.entries.single().isZero)
        assertEquals(0, zeroOnly.scale.signum())
        assertEquals(0, zeroOnly.amountAt(0.5).signum())
    }

    @Test fun startingValueAloneHasNoConsumptionMarksOrCoverage() {
        val source = replacementTracker("2026-10-28", null)
        val tracker = source.copy(
            readings = source.readings.filter { it.installationId == "new" },
            installations = listOf(MeterInstallation("new", LocalDate.parse("2026-10-28"))),
        )
        val temperatures = mapOf(LocalDate.parse("2026-10-28") to BigDecimal("9.25"))
        for (period in listOf(october, year)) {
            val data = dashboard(tracker, period)
            val absolute = chartProjection(period, data, temperatures)
            val daily = chartProjection(period, data, temperatures, ConsumptionChartMode.DAILY_ESTIMATE,
                dailyConsumptionRates(tracker, period))
            assertTrue(absolute.entries.isEmpty())
            assertTrue(daily.entries.isEmpty())
            assertEquals(BigDecimal.ZERO, absolute.scale)
            assertEquals(BigDecimal.ZERO, daily.scale)
            assertTrue(absolute.temperatures.isEmpty())
            assertTrue(daily.temperatures.isEmpty())
            assertNull(data.total.cubicMetres)
        }
    }

    @Test fun mixedOpeningDayUsesOnlyEligibleRateAndKeepsTemperatureInBothModes() {
        val tracker = replacementTracker("2026-10-28", "13.0000", 1)
        val temperatures = mapOf(
            LocalDate.parse("2026-10-28") to BigDecimal("9"),
            LocalDate.parse("2026-10-29") to BigDecimal("11"),
        )
        for (period in listOf(october, year)) {
            val data = dashboard(tracker, period)
            val absolute = chartProjection(period, data, temperatures)
            val daily = chartProjection(period, data, temperatures, ConsumptionChartMode.DAILY_ESTIMATE,
                dailyConsumptionRates(tracker, period))
            assertEquals(absolute.temperatures, daily.temperatures)
            val expected = if (period.type == PeriodType.YEAR) listOf("4") else listOf("5", "3")
            assertEquals(expected.size, daily.entries.size)
            expected.zip(daily.entries).forEach { (value, entry) ->
                assertEquals(0, BigDecimal(value).compareTo(entry.value))
            }
            assertEquals(BigDecimal("8.0000"), data.total.cubicMetres)
        }
    }

    @Test fun emptyBaselineAndOutsidePeriodHaveNoMarksOrInventedScale() {
        for (readings in listOf(
            emptyList(),
            listOf(reading("2026-10-01", "1000")),
            listOf(reading("2026-09-01", "1000"), reading("2026-09-02", "1004")),
        )) {
            val projected = chartProjection(october, dashboard(readings, october))
            assertEquals(31, projected.positionCount)
            assertTrue(projected.entries.isEmpty())
            assertEquals(BigDecimal.ZERO, projected.scale)
            assertEquals(0, projected.amountAt(1.0).signum())
        }
    }

    @Test fun singleCoveredDayRetainsItsPositionAndOriginalDecimal() {
        val source = bucket("2026-10-20", "12.340000")
        val data = snapshot(source)
        val projected = chartProjection(october, data)
        val entry = projected.entries.single()
        assertSame(source.amount.cubicMetres, entry.value)
        assertSame(source.range, entry.range)
        assertEquals(19, entry.offset)
        assertEquals(1.0, entry.normalizedY, 0.0)
        assertFalse(entry.isZero)
        assertEquals(BigDecimal("12.340000"), projected.scale)
        assertEquals(BigDecimal("987.654321"), data.total.cubicMetres)
    }

    @Test fun zerosStayCoveredButMissingDaysHaveNoEntries() {
        val projected = chartProjection(
            october,
            snapshot(bucket("2026-10-31", "0.000"), bucket("2026-10-02", "0")),
        )
        assertEquals(listOf(1, 30), projected.entries.map { it.offset })
        assertTrue(projected.entries.all { it.isZero && it.normalizedY == 0.0 })
        assertEquals(0, projected.scale.signum())
        assertEquals(0, projected.amountAt(0.5).signum())
        assertEquals(31, projected.positionCount)
    }

    @Test fun temperaturePointsAlignByDayKeepGapsAndDoNotChangeConsumptionBars() {
        val data = snapshot(bucket("2026-10-01", "2"), bucket("2026-10-03", "4"))
        val before = chartProjection(october, data)
        val temperatures = mapOf(
            LocalDate.parse("2026-10-01") to BigDecimal("8"),
            LocalDate.parse("2026-10-03") to BigDecimal("10"),
        )
        val after = chartProjection(october, data, temperatures)

        assertEquals(before.entries, after.entries)
        assertEquals(listOf(0, 2), after.temperatures.map { it.offset })
        assertEquals(listOf(BigDecimal("8"), BigDecimal("10")), after.temperatures.map { it.celsius })
        assertEquals(
            listOf(listOf(0), listOf(2)),
            temperatureSegments(after.temperatures).map { segment -> segment.map { it.offset } },
        )
    }

    @Test fun temperatureWithoutAConsumptionBarIsNotPlotted() {
        val data = snapshot(bucket("2026-10-01", "2"))
        val temperatures = mapOf(
            LocalDate.parse("2026-10-01") to BigDecimal("8"),
            LocalDate.parse("2026-10-02") to BigDecimal("9"),
        )

        assertEquals(listOf(0), chartProjection(october, data, temperatures).temperatures.map { it.offset })
    }

    @Test fun weeklyRowTemperatureMeansMatchDailyChartTooltipValues() {
        val temperatures = mapOf(
            LocalDate.parse("2026-10-05") to BigDecimal("4"),
            LocalDate.parse("2026-10-06") to BigDecimal("6"),
            LocalDate.parse("2026-10-07") to BigDecimal("8"),
        )
        val period = CalendarPeriod(PeriodType.WEEK, LocalDate.parse("2026-10-05"))
        val dashboard = snapshot(
            bucket("2026-10-05", "2"),
            bucket("2026-10-06", "3"),
            bucket("2026-10-07", "4"),
        )
        val rowMeans = temperatures.keys.associateWith { date ->
            requireNotNull(TemperatureAggregation.mean(date..date, temperatures))
        }
        val tooltipTemperatures = chartProjection(period, dashboard, temperatures).temperatures
            .associate { entry -> period.start.plusDays(entry.offset.toLong()) to entry.celsius }

        assertEquals(rowMeans, tooltipTemperatures)
    }

    @Test fun yearTemperatureProjectionUsesMonthlyMeansAndPreservesEmptyMonths() {
        val data = snapshot(bucket("2026-10-01", "2"))
        val temperatures = mapOf(
            LocalDate.parse("2026-10-01") to BigDecimal("8"),
            LocalDate.parse("2026-10-31") to BigDecimal("10"),
        )

        val projected = chartProjection(year, data, temperatures)

        assertEquals(listOf(9), projected.temperatures.map { it.offset })
        assertEquals(listOf(BigDecimal("9")), projected.temperatures.map { it.celsius })
    }

    @Test fun unknownBucketDoesNotBecomeAZeroMark() {
        val missing = ConsumptionBucket(
            DateRange(october.start, october.start),
            ConsumptionAmount(null, 0, 1, false),
        )
        val data = Dashboard(
            ConsumptionAmount(BigDecimal.ZERO, 1, 31, false),
            listOf(missing, bucket("2026-10-02", "0")),
        )
        assertEquals(listOf(1), chartProjection(october, data).entries.map { it.offset })
    }

    @Test fun weekOffsetsUseCalendarDaysAcrossMonthBoundary() {
        val week = CalendarPeriod(PeriodType.WEEK, LocalDate.parse("2026-09-28"))
        val data = snapshot(bucket("2026-10-04", "2"), bucket("2026-09-29", "1"))
        val projected = chartProjection(week, data)
        assertEquals(7, projected.positionCount)
        assertEquals(listOf(1, 6), projected.entries.map { it.offset })
        assertEquals(listOf(0.5, 1.0), projected.entries.map { it.normalizedY })
    }

    @Test fun octoberExampleUsesDailyOrMonthlyAuthoritativeBuckets() {
        val readings = listOf(reading("2026-10-01", "1000"), reading("2026-10-04", "1012"))
        val monthData = dashboard(readings, october)
        val daily = chartProjection(october, monthData)
        assertEquals(listOf(1, 2, 3), daily.entries.map { it.offset })
        assertEquals(listOf(BigDecimal("4"), BigDecimal("4"), BigDecimal("4")), daily.entries.map { it.value })
        val yearData = dashboard(readings, year)
        val monthly = chartProjection(year, yearData)
        assertEquals(12, monthly.positionCount)
        assertEquals(9, monthly.entries.single().offset)
        assertEquals(BigDecimal("12"), monthly.entries.single().value)
        assertEquals(monthData.total.cubicMetres, yearData.total.cubicMetres)
    }

    @Test fun crossMonthAllocationKeepsActualMonthOffsetsAndExactValues() {
        val readings = listOf(reading("2026-01-30", "1000"), reading("2026-03-01", "1030"))
        val data = dashboard(readings, year)
        val projected = chartProjection(year, data)
        assertEquals(listOf(0, 1, 2), projected.entries.map { it.offset })
        assertEquals(listOf(BigDecimal("1"), BigDecimal("28"), BigDecimal("1")), projected.entries.map { it.value })
        data.buckets.zip(projected.entries).forEach { (source, entry) ->
            assertSame(source.amount.cubicMetres, entry.value)
            assertEquals(source.range, entry.range)
        }
        assertEquals(BigDecimal("30"), data.total.cubicMetres)
    }

    @Test fun largeTinyAndMixedDecimalsOnlyNormalizeAtTheGraphicsBoundary() {
        for (values in listOf(
            listOf("1E+1000", "2E+1000"),
            listOf("1E-1000", "2E-1000"),
            listOf("1E-1000", "1E+1000"),
        )) {
            val data = snapshot(bucket("2026-10-02", values[0]), bucket("2026-10-31", values[1]))
            val projected = chartProjection(october, data)
            assertEquals(1.0, projected.entries.last().normalizedY, 0.0)
            projected.entries.forEachIndexed { index, entry ->
                assertTrue(entry.normalizedY.isFinite())
                assertTrue(entry.normalizedY > 0.0 && entry.normalizedY <= 1.0)
                assertFalse(entry.isZero)
                assertEquals(BigDecimal(values[index]), entry.value)
                assertSame(data.buckets[index].amount.cubicMetres, entry.value)
            }
            assertEquals(0, projected.scale.compareTo(projected.amountAt(1.0)))
        }
    }

    @Test fun allCalendarPositionsFitEvenForAFullMonth() {
        val data = dashboard(
            listOf(reading("2026-09-30", "0"), reading("2026-10-31", "31")),
            october,
        )
        val projected = chartProjection(october, data)
        assertEquals(31, projected.entries.size)
        assertEquals((0..30).toList(), projected.entries.map { it.offset })
    }

    @Test fun thirdsPreserveOriginalPrecisionWithoutReplacingIndependentTotal() {
        val data = dashboard(
            listOf(reading("2026-10-01", "0"), reading("2026-10-04", "1")),
            october,
        )
        val projected = chartProjection(october, data)
        assertEquals(BigDecimal.ONE, data.total.cubicMetres)
        data.buckets.zip(projected.entries).forEach { (source, entry) ->
            assertSame(source.amount.cubicMetres, entry.value)
            assertEquals(BigDecimal("0.3333333333333333333333333333333333"), entry.value)
        }
        assertNotEquals(data.total.cubicMetres, projected.entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.value })
    }

    @Test fun weeklyRowsPartitionFullyCoveredMonthAndPreserveUnroundedSums() {
        val data = dashboard(
            listOf(reading("2026-09-30", "0"), reading("2026-10-31", "31")),
            october,
        )
        val rows = weeklyRows(october, data.buckets)

        assertTrue(rows.size <= 6)
        assertEquals(LocalDate.parse("2026-10-01"), rows.first().range.start)
        assertEquals(LocalDate.parse("2026-10-31"), rows.last().range.endInclusive)
        assertTrue(rows.all { it.range.days <= 7 })
        assertEquals(31L, rows.sumOf { it.amount.coveredDays })
        assertEquals(0, data.total.cubicMetres!!.compareTo(
            rows.fold(BigDecimal.ZERO) { sum, row -> sum.add(requireNotNull(row.amount.cubicMetres)) },
        ))
        assertEquals(31, data.buckets.size)
        assertEquals((0..30).toList(), chartProjection(october, data).entries.map { it.offset })
        assertEquals(BigDecimal("31"), data.total.cubicMetres)

        val precise = weeklyRows(
            october,
            listOf(bucket("2026-10-01", "1.2300"), bucket("2026-10-04", "2.00")),
        )
        assertEquals(BigDecimal("3.2300"), precise.single().amount.cubicMetres)
    }

    @Test fun weeklyRowsKeepPartialAndKnownZeroWeeksAndOmitUncoveredWeeks() {
        val rows = weeklyRows(
            october,
            listOf(
                bucket("2026-10-03", "1.25", estimated = true),
                bucket("2026-10-04", "0"),
                bucket("2026-10-12", "2.750"),
            ),
        )

        assertEquals(2, rows.size)
        assertEquals(DateRange(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-04")), rows[0].range)
        assertEquals(BigDecimal("1.25"), rows[0].amount.cubicMetres)
        assertEquals(2L, rows[0].amount.coveredDays)
        assertEquals(4L, rows[0].amount.totalDays)
        assertTrue(rows[0].amount.estimatedDistribution)
        assertEquals(DateRange(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-18")), rows[1].range)
        assertEquals(BigDecimal("2.750"), rows[1].amount.cubicMetres)
        assertEquals(1L, rows[1].amount.coveredDays)
        assertEquals(7L, rows[1].amount.totalDays)
    }

    @Test fun weeklyRowsClipMonthsStartingOnMondayOrSundayIncludingSingleDayTail() {
        val mondayMonth = CalendarPeriod(PeriodType.MONTH, LocalDate.parse("2025-09-01"))
        val mondayRows = weeklyRows(
            mondayMonth,
            listOf(bucket("2025-09-01", "1"), bucket("2025-09-30", "1")),
        )
        assertEquals(DateRange(LocalDate.parse("2025-09-01"), LocalDate.parse("2025-09-07")), mondayRows.first().range)
        assertEquals(DateRange(LocalDate.parse("2025-09-29"), LocalDate.parse("2025-09-30")), mondayRows.last().range)

        val sundayMonth = CalendarPeriod(PeriodType.MONTH, LocalDate.parse("2025-06-01"))
        val sundayRows = weeklyRows(
            sundayMonth,
            listOf(bucket("2025-06-01", "1"), bucket("2025-06-30", "0")),
        )
        assertEquals(DateRange(LocalDate.parse("2025-06-01"), LocalDate.parse("2025-06-01")), sundayRows.first().range)
        assertEquals(DateRange(LocalDate.parse("2025-06-30"), LocalDate.parse("2025-06-30")), sundayRows.last().range)
        assertEquals(BigDecimal.ZERO, sundayRows.last().amount.cubicMetres)
    }

    @Test fun mutationAndPeriodSnapshotsNeverRetainOldValues() {
        val readings = mutableListOf(reading("2026-10-01", "100"), reading("2026-10-04", "112"))
        val before = chartProjection(october, dashboard(readings, october))
        readings[1] = reading("2026-10-04", "106")
        val edited = chartProjection(october, dashboard(readings, october))
        assertEquals(BigDecimal("4"), before.entries.first().value)
        assertEquals(BigDecimal("2"), edited.entries.first().value)
        assertNotEquals(before, edited)
        assertEquals(before.entries.map { it.normalizedY }, edited.entries.map { it.normalizedY })
        val changedPeriod = chartProjection(year, dashboard(readings, year))
        assertNotEquals(edited, changedPeriod)
        assertEquals(9, changedPeriod.entries.single().offset)
        readings.removeAt(1)
        assertTrue(chartProjection(october, dashboard(readings, october)).entries.isEmpty())
        assertEquals(3, before.entries.size)
        readings += reading("2026-10-02", "100")
        assertTrue(chartProjection(october, dashboard(readings, october)).entries.single().isZero)
    }

    @Test fun projectionCopiesMutableBucketCollections() {
        val buckets = mutableListOf(bucket("2026-10-02", "1"))
        val data = Dashboard(ConsumptionAmount(BigDecimal.ONE, 1, 31, false), buckets)
        val first = chartProjection(october, data)
        buckets.clear()
        buckets += bucket("2026-10-31", "2")
        val second = chartProjection(october, data)
        assertEquals(1, first.entries.single().offset)
        assertEquals(BigDecimal.ONE, first.entries.single().value)
        assertEquals(30, second.entries.single().offset)
        assertEquals(BigDecimal("2"), second.entries.single().value)
    }

    @Test fun axisLabelsAreLocalizedAndExtremeMagnitudesStayCompact() {
        assertEquals("12,1", chartAmountText(BigDecimal("12.125"), Locale.GERMANY))
        assertEquals("0", chartAmountText(BigDecimal.ZERO, Locale.GERMANY))
        assertEquals("1,25E1000", chartAmountText(BigDecimal("1.25E+1000"), Locale.GERMANY))
        assertEquals("1,25E-1000", chartAmountText(BigDecimal("1.25E-1000"), Locale.GERMANY))
    }

    @Test fun zeroAxisMeasurementLabelsAreNeverEmpty() {
        val projected = chartProjection(october, snapshot(bucket("2026-10-02", "0")))
        // Vico measures minimum, midpoint, and maximum even when only one tick is drawn.
        listOf(0.0, 0.5, 1.0).forEach { value ->
            assertEquals("0", chartAxisText(projected, value, Locale.GERMANY))
        }
    }

    private fun replacementTracker(date: String, next: String? = "16", gap: Long = 3): TrackerData {
        val boundary = LocalDate.parse(date)
        val final = Reading("old-final", boundary, BigDecimal("1105"))
        val initial = Reading("new-initial", boundary, BigDecimal("10.0000"), "new")
        return TrackerData(
            listOf(Reading("old-first", boundary.minusDays(1), BigDecimal("1100")), final, initial) +
                listOfNotNull(next?.let { Reading("new-next", boundary.plusDays(gap), BigDecimal(it), "new") }),
            listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID), MeterInstallation("new", boundary)),
        )
    }

    private fun assertAuthoritativeBuckets(tracker: TrackerData, period: CalendarPeriod, expectedTotal: String) {
        val data = dashboard(tracker, period)
        val projected = chartProjection(period, data)
        assertEquals(0, BigDecimal(expectedTotal).compareTo(data.total.cubicMetres))
        assertEquals(data.buckets.size, projected.entries.size)
        data.buckets.zip(projected.entries).forEach { (bucket, entry) ->
            assertEquals(bucket.range, entry.range)
            assertSame(bucket.amount.cubicMetres, entry.value)
            assertEquals(bucket.amount.cubicMetres!!.signum() == 0, entry.isZero)
        }
    }

    @Test fun replacementBarsFollowCoveredBucketsAcrossWeekMonthAndYearBoundaries() {
        for ((type, date) in listOf(
            PeriodType.WEEK to "2026-11-01",
            PeriodType.MONTH to "2026-10-31",
            PeriodType.YEAR to "2026-12-31",
        )) {
            val tracker = replacementTracker(date)
            val boundary = LocalDate.parse(date)
            val oldPeriod = CalendarPeriod.containing(type, boundary)
            val newPeriod = CalendarPeriod.containing(type, boundary.plusDays(1))
            assertAuthoritativeBuckets(tracker, oldPeriod, "5")
            assertAuthoritativeBuckets(tracker, newPeriod, "6.0000")
            val old = chartProjection(oldPeriod, dashboard(tracker, oldPeriod))
            val new = chartProjection(newPeriod, dashboard(tracker, newPeriod))
            assertEquals(old.positionCount - 1, old.entries.single().offset)
            assertEquals(if (type == PeriodType.YEAR) listOf(0) else listOf(0, 1, 2), new.entries.map { it.offset })
            assertTrue(new.entries.all { !it.isZero })
        }
    }

    @Test fun replacementDayBaselineIsExcludedAlongsideOldMeterUsage() {
        val source = replacementTracker("2026-10-28", "13.0000", 1)
        val data = dashboard(source, october)
        val chart = chartProjection(october, data)
        assertEquals(listOf(27, 28), chart.entries.map { it.offset })
        assertEquals(listOf(BigDecimal("5"), BigDecimal("3.0000")), chart.entries.map { it.value })
        assertAuthoritativeBuckets(source, october, "8.0000")
        assertAuthoritativeBuckets(source, year, "8.0000")
    }

    @Test fun replacementFirstReadingIsOnlyABaselineAndEqualNextReadingIsKnownZero() {
        val source = replacementTracker("2026-10-28", null)
        val before = chartProjection(october, dashboard(source, october))
        assertEquals(listOf(27), before.entries.map { it.offset })
        assertEquals(listOf(BigDecimal("5")), before.entries.map { it.value })
        val initial = source.readings.single { it.id == "new-initial" }
        val tracker = source.copy(readings = source.readings + initial.copy(id = "zero-next",
            date = initial.date.plusDays(1), observedAt = initial.observedAt.plusSeconds(86400)))
        val after = chartProjection(october, dashboard(tracker, october))
        assertEquals(listOf(27, 28), after.entries.map { it.offset })
        assertTrue(after.entries.last().isZero)
        assertEquals(0.0, after.entries.last().normalizedY, 0.0)
        assertFalse(after.entries.first().isZero)
        assertAuthoritativeBuckets(tracker, october, "5.0000")
    }

    @Test fun replacementThirdsKeepIndependentExactTotalsAndOriginalBucketPrecision() {
        val source = replacementTracker("2026-10-04", "11.0000")
        val tracker = source.copy(readings = source.readings.map {
            when (it.id) {
                "old-first" -> it.copy(date = LocalDate.parse("2026-10-01"), observedAt = java.time.Instant.parse("2026-10-01T08:00:00Z"))
                "old-final" -> it.copy(cubicMetres = BigDecimal("1101.0000"))
                else -> it
            }
        })
        assertAuthoritativeBuckets(tracker, october, "2.0000")
        assertAuthoritativeBuckets(tracker, year, "2.0000")
        val data = dashboard(tracker, october)
        val projected = chartProjection(october, data)
        assertEquals((1..6).toList(), projected.entries.map { it.offset })
        val third = BigDecimal("0.3333333333333333333333333333333333")
        assertEquals(
            List(6) { third },
            projected.entries.map { it.value },
        )
        assertNotEquals(data.total.cubicMetres, projected.entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.value })
    }

    @Test fun boundaryCorrectionsAndOrdinaryDeletionRefreshMarksWithoutJoiningMeters() {
        val source = replacementTracker("2026-10-28", "13.0000", 1)
        val before = chartProjection(october, dashboard(source, october))
        val corrected = source.copy(readings = source.readings.map {
            when (it.id) {
                "old-final" -> it.copy(cubicMetres = BigDecimal("1106"))
                "new-initial" -> it.copy(cubicMetres = BigDecimal("11.0000"))
                else -> it
            }
        })
        val edited = chartProjection(october, dashboard(corrected, october))
        assertEquals(listOf(BigDecimal("6"), BigDecimal("2.0000")), edited.entries.map { it.value })
        assertEquals(listOf(BigDecimal("5"), BigDecimal("3.0000")), before.entries.map { it.value })
        assertAuthoritativeBuckets(corrected, october, "8.0000")
        val deleted = corrected.copy(readings = corrected.readings.filterNot { it.id == "new-next" })
        assertEquals(listOf(27), chartProjection(october, dashboard(deleted, october)).entries.map { it.offset })
        assertAuthoritativeBuckets(deleted, october, "6.0000")
        val withoutOldFinal = source.copy(readings = source.readings.filterNot { it.id == "old-final" })
        assertEquals(listOf(28), chartProjection(october, dashboard(withoutOldFinal, october)).entries.map { it.offset })
        assertAuthoritativeBuckets(withoutOldFinal, october, "3.0000")
        val withoutNewFirst = source.copy(readings = source.readings.filterNot { it.id == "new-initial" })
        assertEquals(listOf(27), chartProjection(october, dashboard(withoutNewFirst, october)).entries.map { it.offset })
        assertAuthoritativeBuckets(withoutNewFirst, october, "5.0000")
    }
}
