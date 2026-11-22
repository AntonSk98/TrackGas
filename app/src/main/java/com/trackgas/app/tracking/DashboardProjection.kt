package com.trackgas.app.tracking

import com.trackgas.app.temperature.DailyTemperatureEntity
import com.trackgas.app.temperature.IntervalTemperatureEntity
import com.trackgas.app.temperature.MeterLocation
import com.trackgas.app.temperature.TemperatureAggregation
import java.math.BigDecimal
import java.time.LocalDate

internal data class DashboardProjectionInput(
    val tracker: TrackerData,
    val period: CalendarPeriod,
    val chartMode: ConsumptionChartMode,
    val intervalMeans: List<IntervalTemperatureEntity>,
    val dailyMeans: List<DailyTemperatureEntity>,
    val location: MeterLocation?,
    val temperatureGeneration: Long,
)

internal data class DashboardProjection(
    val readings: List<Reading>,
    val dashboard: Dashboard,
    val dailyRates: Map<LocalDate, BigDecimal>,
    val temperatures: Map<LocalDate, BigDecimal>,
    val intervalTemperatures: Map<LocalDate, BigDecimal>,
    val dailyTemperatures: Map<LocalDate, BigDecimal>,
)

internal fun prepareDashboardProjection(input: DashboardProjectionInput): DashboardProjection {
    val tracker = input.tracker
    val intervals = readingIntervals(tracker)
    val installations = tracker.installations
        .sortedWith(compareBy<MeterInstallation> { it.startDate != null }.thenBy { it.startDate })
        .mapIndexed { index, installation -> installation.id to index }
        .toMap()
    val readings = tracker.readings.sortedWith(
        compareByDescending<Reading> { it.date }.thenByDescending { installations.getValue(it.installationId) },
    )
    val intervalTemperatures = TemperatureAggregation.temperaturesByConsumptionDate(
        readings, input.intervalMeans, input.location,
    )
    val dailyTemperatures = TemperatureAggregation.dailyTemperaturesByConsumptionDate(
        readings, input.dailyMeans, input.location,
    )
    val selectedTemperatures = when (input.chartMode) {
        ConsumptionChartMode.ABSOLUTE -> intervalTemperatures
        ConsumptionChartMode.DAILY_ESTIMATE -> dailyTemperatures
    }
    return DashboardProjection(
        readings = readings,
        dashboard = dashboardFromIntervals(intervals, input.period),
        dailyRates = dailyConsumptionRates(intervals, input.period),
        temperatures = selectedTemperatures,
        intervalTemperatures = intervalTemperatures,
        dailyTemperatures = dailyTemperatures,
    )
}
