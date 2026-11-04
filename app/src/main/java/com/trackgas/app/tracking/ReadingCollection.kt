package com.trackgas.app.tracking

internal enum class ReadingCollectionViolation {
    DUPLICATE_IDENTITY, DUPLICATE_DATE, DECREASING_VALUE,
    INVALID_INSTALLATION, INVALID_CHAIN, INVALID_ENDPOINT, OUTSIDE_LIFETIME, OVERLAPPING_COVERAGE,
}

/** Checks relationships only; each [Reading] already enforces its intrinsic invariants. */
internal fun readingCollectionViolation(readings: List<Reading>): ReadingCollectionViolation? {
    if (readings.map { it.id }.toSet().size != readings.size) {
        return ReadingCollectionViolation.DUPLICATE_IDENTITY
    }
    for (installation in readings.groupBy { it.installationId }.values) {
        val chronological = installation.sortedBy { it.date }
        for (index in 1 until chronological.size) {
            val earlier = chronological[index - 1]
            val later = chronological[index]
            if (earlier.date == later.date) return ReadingCollectionViolation.DUPLICATE_DATE
            if (earlier.cubicMetres > later.cubicMetres) return ReadingCollectionViolation.DECREASING_VALUE
        }
    }
    return null
}

internal fun readingCollectionViolation(tracker: TrackerData): ReadingCollectionViolation? {
    readingCollectionViolation(tracker.readings)?.let { return it }
    val installations = tracker.installations.associateBy { it.id }
    val boundaries = tracker.replacements
    if (installations.isEmpty() || installations.size != tracker.installations.size ||
        tracker.readings.any { it.installationId !in installations }
    ) return ReadingCollectionViolation.INVALID_INSTALLATION
    if (boundaries.map { it.id }.toSet().size != boundaries.size ||
        boundaries.map { it.date }.toSet().size != boundaries.size ||
        boundaries.map { it.oldInstallationId }.toSet().size != boundaries.size ||
        boundaries.map { it.newInstallationId }.toSet().size != boundaries.size ||
        boundaries.any { it.oldInstallationId !in installations || it.newInstallationId !in installations }
    ) return ReadingCollectionViolation.INVALID_CHAIN

    val readings = tracker.readings.associateBy { it.id }
    val incoming = boundaries.associateBy { it.newInstallationId }
    val outgoing = boundaries.associateBy { it.oldInstallationId }
    val visited = mutableSetOf<String>()
    for (root in tracker.installations.filterNot { it.id in incoming }) {
        var current = root.id
        var previousDate: java.time.LocalDate? = null
        while (true) {
            if (!visited.add(current)) return ReadingCollectionViolation.INVALID_CHAIN
            val boundary = outgoing[current] ?: break
            if (boundary.newInstallationId == current ||
                previousDate?.let { boundary.date <= it } == true ||
                installations[boundary.newInstallationId]?.startDate != boundary.date
            ) return ReadingCollectionViolation.INVALID_CHAIN
            val final = readings[boundary.finalReadingId]
            val initial = readings[boundary.initialReadingId]
            if (final == null || initial == null || final.id == initial.id ||
                final.installationId != boundary.oldInstallationId ||
                initial.installationId != boundary.newInstallationId ||
                final.date != boundary.date || initial.date != boundary.date
            ) return ReadingCollectionViolation.INVALID_ENDPOINT
            current = boundary.newInstallationId
            previousDate = boundary.date
        }
    }
    if (visited != installations.keys) return ReadingCollectionViolation.INVALID_CHAIN
    val endpoints = boundaries.flatMap { listOf(it.finalReadingId, it.initialReadingId) }
    if (endpoints.toSet().size != endpoints.size) return ReadingCollectionViolation.INVALID_ENDPOINT
    for (reading in tracker.readings) {
        // Independent roots retain installation metadata for ordering, but it does not define
        // a replacement lifetime after the corresponding relationship has been removed.
        val start = incoming[reading.installationId]?.date
        val end = outgoing[reading.installationId]?.date
        if (start?.let { reading.date < it } == true || end?.let { reading.date > it } == true
        ) return ReadingCollectionViolation.OUTSIDE_LIFETIME
    }
    if (hasOverlappingReadingCoverage(tracker.readings)) return ReadingCollectionViolation.OVERLAPPING_COVERAGE
    return null
}

internal fun hasOverlappingReadingCoverage(readings: List<Reading>): Boolean {
    return overlappingReadingCoverage(readings) != null
}

internal fun overlappingReadingCoverage(readings: List<Reading>): Pair<ReadingInterval, ReadingInterval>? {
    val intervals = readings.groupBy { it.installationId }.values
        .flatMap { meterReadings ->
            meterReadings.sortedBy { it.date }.zipWithNext { earlier, later -> ReadingInterval(earlier, later) }
        }
        .sortedBy { it.firstCoveredDate }
    return intervals.zipWithNext().firstOrNull { (earlier, later) ->
        earlier.lastCoveredDate >= later.firstCoveredDate
    }?.let { it.first to it.second }
}
