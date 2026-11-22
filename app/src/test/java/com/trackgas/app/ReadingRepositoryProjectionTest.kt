package com.trackgas.app

import com.trackgas.app.temperature.DailyTemperatureEntity
import com.trackgas.app.temperature.IntervalTemperatureEntity
import com.trackgas.app.temperature.MeterLocation
import com.trackgas.app.temperature.TemperatureAggregation
import com.trackgas.app.tracking.*
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingRepositoryProjectionTest {
    @Test fun periodDataKeepsTheNeighborIntervalAcrossBothBoundaries() {
        val tracker = TrackerData(
            readings = listOf(
                reading("before", "2026-01-30", "100"),
                reading("after", "2026-03-01", "130"),
            ),
        )
        val period = CalendarPeriod.containing(PeriodType.MONTH, LocalDate.parse("2026-02-15"))

        val scoped = dashboardDataForPeriod(tracker, period.range)

        assertEquals(setOf("before", "after"), scoped.readings.map { it.id }.toSet())
        assertEquals(dashboard(tracker, period), dashboard(scoped, period))
    }

    @Test fun historyCursorContinuesInStableNewestFirstInstallationOrder() {
        val date = LocalDate.parse("2026-05-01")
        val tracker = TrackerData(
            readings = listOf(
                reading("old-meter", date.toString(), "1", "old"),
                reading("middle-meter", date.toString(), "1", "middle"),
                reading("new-meter", date.toString(), "1", "new"),
            ),
            installations = listOf(
                MeterInstallation("old", LocalDate.parse("2025-01-01")),
                MeterInstallation("middle", LocalDate.parse("2025-02-01")),
                MeterInstallation("new", LocalDate.parse("2025-03-01")),
            ),
        )

        val first = historyPageFromTracker(tracker, cursor = null, pageSize = 2)
        val second = historyPageFromTracker(tracker, first.nextCursor, pageSize = 2)

        assertEquals(listOf("new-meter", "middle-meter"), first.readings.map { it.id })
        assertEquals(listOf("old-meter"), second.readings.map { it.id })
        assertEquals(null, second.nextCursor)
    }

    @Test fun scopedDashboardMatchesFullHistoryAcrossPeriodsReplacementsGapsAndTemperatures() {
        val tracker = TrackerData(
            readings = listOf(
                reading("before-year", "2025-12-20", "80", "old"),
                reading("old-meter", "2026-02-20", "130", "old"),
                reading("starting", "2026-03-01", "0", "current"),
                reading("march", "2026-03-15", "10", "current"),
                reading("april", "2026-04-15", "40", "current"),
                reading("june", "2026-06-10", "100", "current"),
                reading("after-year", "2027-01-10", "110", "current"),
            ),
            installations = listOf(
                MeterInstallation("old"),
                MeterInstallation("current", LocalDate.parse("2026-03-01")),
            ),
        )
        val location = MeterLocation("Test", 1.0, 2.0)
        val fingerprint = "${location.displayName}|${location.latitude}|${location.longitude}"
        val intervalMeans = com.trackgas.app.temperature.readingIntervals(tracker.readings).map { interval ->
            IntervalTemperatureEntity(
                interval.earlier.id,
                interval.later.id,
                interval.earlier.installationId,
                interval.start.epochSecond,
                interval.end.epochSecond,
                fingerprint,
                "12.5",
            )
        }
        val dailyMeans = listOf(DailyTemperatureEntity("2026-04-15", fingerprint, "8"))

        for (type in PeriodType.entries) {
            val period = CalendarPeriod.containing(type, LocalDate.parse("2026-04-15"))
            val scoped = dashboardDataForPeriod(tracker, period.range)
            fun Map<LocalDate, *>.inPeriod() =
                filterKeys { it in period.range.start..period.range.endInclusive }

            assertEquals(dashboard(tracker, period), dashboard(scoped, period))
            assertEquals(dailyConsumptionRates(tracker, period), dailyConsumptionRates(scoped, period))
            assertEquals(
                TemperatureAggregation.temperaturesByConsumptionDate(tracker.readings, intervalMeans, location).inPeriod(),
                TemperatureAggregation.temperaturesByConsumptionDate(scoped.readings, intervalMeans, location).inPeriod(),
            )
            assertEquals(
                TemperatureAggregation.dailyTemperaturesByConsumptionDate(tracker.readings, dailyMeans, location).inPeriod(),
                TemperatureAggregation.dailyTemperaturesByConsumptionDate(scoped.readings, dailyMeans, location).inPeriod(),
            )
        }
    }

    @Test fun pagedHistoryContextPreservesFullHistoryEstimatesAndStartingMarkers() {
        val tracker = TrackerData(
            readings = listOf(
                reading("old-start", "2026-01-01", "0", "old"),
                reading("old-next", "2026-01-05", "10", "old"),
                reading("new-start", "2026-01-10", "0", "new"),
                reading("new-next", "2026-01-14", "12", "new"),
                reading("new-last", "2026-01-20", "30", "new"),
            ),
            installations = listOf(
                MeterInstallation("old", LocalDate.parse("2026-01-01")),
                MeterInstallation("new", LocalDate.parse("2026-01-10")),
            ),
        )
        val readings = mutableListOf<Reading>()
        val context = mutableListOf<Reading>()
        val startingIds = mutableSetOf<String>()
        var cursor: HistoryCursor? = null
        do {
            val page = historyPageFromTracker(tracker, cursor, pageSize = 2)
            readings += page.readings
            context += page.predecessorReadings
            startingIds += page.startingReadingIds
            cursor = page.nextCursor
        } while (cursor != null)
        val pagedTracker = TrackerData((readings + context).distinctBy { it.id }, tracker.installations)

        assertEquals(tracker.readings.sortedWith(historyOrder(tracker.installations)), readings)
        assertEquals(history24HourEstimates(tracker), history24HourEstimates(pagedTracker))
        assertEquals(meterStartingReadingIds(tracker), startingIds)
    }

    private fun reading(id: String, date: String, value: String, installationId: String = ORIGINAL_INSTALLATION_ID) =
        Reading(id, LocalDate.parse(date), value.toBigDecimal(), installationId)
}
