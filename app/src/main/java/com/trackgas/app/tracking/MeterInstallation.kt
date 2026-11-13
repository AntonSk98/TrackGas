package com.trackgas.app.tracking

import java.time.LocalDate

// Placeholder for list-only calculations and unloaded UI state, never a persisted installation.
const val ORIGINAL_INSTALLATION_ID = "original"

data class MeterInstallation(val id: String, val startDate: LocalDate? = null) {
    init { require(id.isNotBlank()) { "Installation identity must not be blank" } }
}

/** A committed tracker owns stable lists; consumers must not mutate them after delivery. */
data class TrackerData(
    val readings: List<Reading> = emptyList(),
    val installations: List<MeterInstallation> = readings.map { it.installationId }.distinct().map(::MeterInstallation),
) {
    val currentInstallation: MeterInstallation?
        get() {
            val latestReadingByInstallation = readings.groupBy { it.installationId }
                .mapValues { (_, meterReadings) -> meterReadings.maxOf { it.date } }
            return installations.maxWithOrNull(
                compareBy<MeterInstallation> { installation ->
                    listOfNotNull(installation.startDate, latestReadingByInstallation[installation.id]).maxOrNull()
                }
                    .thenBy { it.startDate != null }
                    .thenBy { it.startDate }
                    .thenBy { it.id },
            )
        }
}

internal fun appendStartedMeter(
    tracker: TrackerData,
    installation: MeterInstallation,
    startingReading: Reading,
): TrackerData {
    require(installation.startDate == startingReading.date)
    require(installation.id == startingReading.installationId)
    require(tracker.installations.none { it.id == installation.id })
    val next = tracker.copy(
        readings = tracker.readings + startingReading,
        installations = tracker.installations + installation,
    )
    check(readingCollectionViolation(next) == null) { "A started meter must produce a valid tracker" }
    return next
}
