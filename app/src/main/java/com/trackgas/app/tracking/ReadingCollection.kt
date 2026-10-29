package com.trackgas.app.tracking

internal enum class ReadingCollectionViolation {
    DUPLICATE_IDENTITY, DUPLICATE_DATE, DECREASING_VALUE,
    INVALID_INSTALLATION, INVALID_CHAIN, INVALID_ENDPOINT, OUTSIDE_LIFETIME,
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
    if (installations.size != tracker.installations.size ||
        tracker.readings.any { it.installationId !in installations }
    ) return ReadingCollectionViolation.INVALID_INSTALLATION
    val original = tracker.installations.singleOrNull { it.startDate == null }
        ?: return ReadingCollectionViolation.INVALID_CHAIN
    if (boundaries.map { it.id }.toSet().size != boundaries.size ||
        boundaries.map { it.date }.toSet().size != boundaries.size ||
        boundaries.map { it.oldInstallationId }.toSet().size != boundaries.size ||
        boundaries.map { it.newInstallationId }.toSet().size != boundaries.size ||
        boundaries.size != installations.size - 1
    ) return ReadingCollectionViolation.INVALID_CHAIN

    val readings = tracker.readings.associateBy { it.id }
    val visited = mutableSetOf(original.id)
    var current = original.id
    var previousDate: java.time.LocalDate? = null
    for (boundary in boundaries.sortedBy { it.date }) {
        if (boundary.oldInstallationId != current ||
            boundary.newInstallationId !in installations ||
            !visited.add(boundary.newInstallationId) ||
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
    if (visited != installations.keys) return ReadingCollectionViolation.INVALID_CHAIN
    val endpoints = boundaries.flatMap { listOf(it.finalReadingId, it.initialReadingId) }
    if (endpoints.toSet().size != endpoints.size) return ReadingCollectionViolation.INVALID_ENDPOINT
    for (reading in tracker.readings) {
        val installation = installations.getValue(reading.installationId)
        val end = boundaries.firstOrNull { it.oldInstallationId == installation.id }?.date
        if (installation.startDate?.let { reading.date < it } == true ||
            end?.let { reading.date > it } == true
        ) return ReadingCollectionViolation.OUTSIDE_LIFETIME
    }
    return null
}
