package com.trackgas.app.tracking

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Measured difference between consecutive, validated cumulative readings.
 *
 * Usage covers `(earlier.date, later.date]`: the earlier date is excluded and
 * the later date is included, so adjoining intervals never count the same day twice.
 * A multi-day interval has a measured total but only an estimated daily distribution.
 * Source readings are retained for recalculation; allocations never become history notes.
 */
data class ReadingInterval(val earlier: Reading, val later: Reading) {
    init {
        require(earlier.installationId == later.installationId) { "Interval readings must belong to the same installation" }
        readingCollectionViolation(listOf(earlier, later))?.let {
            throw IllegalArgumentException("Invalid reading collection: $it")
        }
        require(earlier.date < later.date) { "Interval dates must increase strictly" }
    }

    val firstCoveredDate: LocalDate get() = earlier.date.plusDays(1)
    val lastCoveredDate: LocalDate get() = later.date
    val days: Long get() = ChronoUnit.DAYS.between(earlier.date, later.date)
    val cubicMetres: BigDecimal get() = later.cubicMetres.subtract(earlier.cubicMetres)
    val estimatedDistribution: Boolean get() = days > 1
}

/**
 * Pairs actual readings within each installation, regardless of entry order.
 *
 * Rejects duplicate identities/installation dates, decreasing values, and overlapping coverage.
 * Empty input and a lone baseline produce no intervals.
 */
fun readingIntervals(readings: List<Reading>): List<ReadingInterval> {
    readingCollectionViolation(readings)?.let {
        throw IllegalArgumentException("Invalid reading collection: $it")
    }
    return readings.groupBy { it.installationId }.values
        .flatMap { installation ->
            installation.sortedBy { it.date }.zipWithNext { earlier, later -> ReadingInterval(earlier, later) }
        }
        .sortedBy { it.firstCoveredDate }
        .also(::requireNonOverlapping)
}

/** Validates the complete replacement chain before projecting installation-local usage. */
fun readingIntervals(tracker: TrackerData): List<ReadingInterval> {
    readingCollectionViolation(tracker)?.let {
        throw IllegalArgumentException("Invalid reading collection: $it")
    }
    return readingIntervals(tracker.readings)
}

private fun requireNonOverlapping(intervals: List<ReadingInterval>) {
    for (index in 1 until intervals.size) {
        require(intervals[index - 1].lastCoveredDate < intervals[index].firstCoveredDate) {
            "Allocation intervals must be chronologically ordered and non-overlapping"
        }
    }
}

/** Inclusive calendar-day range used for both selected periods and individual buckets. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init { require(!endInclusive.isBefore(start)) { "Date range must not end before it starts" } }
    val days: Long get() = ChronoUnit.DAYS.between(start, endInclusive) + 1
}

/**
 * Consumption plus internal coverage/provenance metadata for a requested range.
 *
 * @property cubicMetres Null means no covered usage; numeric zero means measured coverage
 * with no consumption. These states must not be treated as interchangeable.
 * @property coveredDays Number of distinct dates with a consumption contribution in this range.
 * @property totalDays Size of the entire requested range, including uncovered dates.
 * @property estimatedDistribution Whether any contribution comes from a multi-day interval.
 * This metadata is retained for calculations/tests but is not shown on the dashboard.
 */
data class ConsumptionAmount(
    val cubicMetres: BigDecimal?,
    val coveredDays: Long,
    val totalDays: Long,
    val estimatedDistribution: Boolean,
) {
    init {
        require(totalDays > 0 && coveredDays in 0..totalDays) { "Coverage must fit a nonempty range" }
        require((cubicMetres == null) == (coveredDays == 0L)) { "Consumption must exist exactly when coverage exists" }
        require(cubicMetres == null || cubicMetres.signum() >= 0) { "Consumption must not be negative" }
        require(!estimatedDistribution || coveredDays > 0) { "Estimated consumption requires coverage" }
    }

    val fullyCovered: Boolean get() = coveredDays == totalDays
}

/**
 * Allocates interval usage to its overlap with [range], without extrapolation.
 *
 * Each contribution is `interval delta * overlapping days / interval days`.
 * Whole intervals retain their exact decimal delta; partial slices use DECIMAL128
 * precision. Nothing is rounded for display here, and totals are never derived from
 * rounded daily rows. Calendar-day overlap avoids daylight-saving duration errors.
 *
 * [intervals] must be ordered by date without overlapping coverage; invalid inputs
 * are rejected even when they lie outside [range]. Gaps are not extrapolated.
 * Work scales with the number of intervals, not the number of skipped days.
 */
fun allocate(intervals: List<ReadingInterval>, range: DateRange): ConsumptionAmount {
    requireNonOverlapping(intervals)
    return allocateContributions(intervals.map(ReadingInterval::asContribution), range)
}

private data class ConsumptionContribution(
    val range: DateRange,
    val cubicMetres: BigDecimal,
    val estimatedDistribution: Boolean,
)

private fun ReadingInterval.asContribution() = ConsumptionContribution(
    DateRange(firstCoveredDate, lastCoveredDate),
    cubicMetres,
    estimatedDistribution,
)

private fun allocateContributions(
    contributions: List<ConsumptionContribution>,
    range: DateRange,
): ConsumptionAmount {
    var total = BigDecimal.ZERO
    var estimated = false
    val coveredRanges = mutableListOf<DateRange>()
    for (contribution in contributions) {
        val start = maxOf(range.start, contribution.range.start)
        val end = minOf(range.endInclusive, contribution.range.endInclusive)
        if (end < start) continue
        val overlap = ChronoUnit.DAYS.between(start, end) + 1
        // Whole contributions retain exact values; partial intervals use high precision.
        total = total.add(
            if (overlap == contribution.range.days) contribution.cubicMetres
            else contribution.cubicMetres.multiply(BigDecimal.valueOf(overlap))
                .divide(BigDecimal.valueOf(contribution.range.days), MathContext.DECIMAL128),
        )
        coveredRanges += DateRange(start, end)
        estimated = estimated || contribution.estimatedDistribution
    }
    val coveredDays = unionCoveredDays(coveredRanges)
    return ConsumptionAmount(if (coveredDays == 0L) null else total, coveredDays, range.days, estimated)
}

private fun unionCoveredDays(ranges: List<DateRange>): Long {
    var days = 0L
    var coveredThrough: LocalDate? = null
    for (range in ranges.sortedBy { it.start }) {
        val previousEnd = coveredThrough
        when {
            previousEnd == null || range.start > previousEnd -> {
                days += range.days
                coveredThrough = range.endInclusive
            }
            range.endInclusive > previousEnd -> {
                days += ChronoUnit.DAYS.between(previousEnd, range.endInclusive)
                coveredThrough = range.endInclusive
            }
        }
    }
    return days
}

/** One daily or monthly slice of consumption, distinct from an entered meter reading. */
data class ConsumptionBucket(val range: DateRange, val amount: ConsumptionAmount)

/**
 * Selected-period projection: an independently calculated total and only covered display rows.
 *
 * The total retains whole-period coverage metadata even though uncovered rows are omitted.
 */
data class Dashboard(
    val total: ConsumptionAmount,
    val buckets: List<ConsumptionBucket>,
)

/**
 * Projects all source readings onto [period], including intervals crossing its boundaries.
 * A first reading on an installation's start date also contributes opening usage on that date.
 *
 * Weekly/monthly periods use daily rows; yearly periods use monthly rows. Uncovered
 * buckets are omitted, while known zero and estimated allocations remain visible.
 * No overlapping interval means a null total and an empty list of rows.
 */
fun dashboard(readings: List<Reading>, period: CalendarPeriod): Dashboard =
    dashboardFromContributions(
        readingIntervals(readings).map(ReadingInterval::asContribution),
        period,
    )

fun dashboard(tracker: TrackerData, period: CalendarPeriod): Dashboard {
    val intervals = readingIntervals(tracker)
    val firstReadingByInstallation = tracker.readings.groupBy { it.installationId }
        .mapValues { (_, readings) -> readings.minBy { it.date } }
    val openingContributions = tracker.installations.mapNotNull { installation ->
        val startDate = installation.startDate ?: return@mapNotNull null
        val firstReading = firstReadingByInstallation[installation.id] ?: return@mapNotNull null
        if (firstReading.date == startDate) {
            ConsumptionContribution(DateRange(startDate, startDate), firstReading.cubicMetres, estimatedDistribution = false)
        } else {
            null
        }
    }
    return dashboardFromContributions(intervals.map(ReadingInterval::asContribution) + openingContributions, period)
}

private fun dashboardFromContributions(
    contributions: List<ConsumptionContribution>,
    period: CalendarPeriod,
): Dashboard {
    val total = allocateContributions(contributions, period.range)
    return Dashboard(
        total,
        if (total.coveredDays == 0L) emptyList()
        else period.buckets().map { ConsumptionBucket(it, allocateContributions(contributions, it)) }
            .filter { it.amount.coveredDays > 0 },
    )
}
