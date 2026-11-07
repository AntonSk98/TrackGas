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
    val installations: List<MeterInstallation> = listOf(MeterInstallation(ORIGINAL_INSTALLATION_ID)),
) {
    val currentInstallation: MeterInstallation
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
            ) ?: error("Tracker must contain an installation")
        }
}
