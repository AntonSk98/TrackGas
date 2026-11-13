package com.trackgas.app.tracking

import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

/** Domain error codes mapped to localized field messages by [ReadingForm]. */
enum class ReadingError { DATE_REQUIRED, FUTURE_DATE, BEFORE_METER_START_DATE, DUPLICATE_DATE, VALUE_REQUIRED, INVALID_NUMBER, NEGATIVE_VALUE, BELOW_PREVIOUS, ABOVE_NEXT, MISSING_READING, TIME_REQUIRED, INVALID_TIME, FUTURE_TIME, TIME_ORDER }

/** Independent date/value errors; both fields may be invalid in the same submission. */
data class ReadingErrors(
    val date: ReadingError? = null,
    val value: ReadingError? = null,
    val time: ReadingError? = null,
) {
    val isEmpty: Boolean get() = date == null && value == null && time == null
}

sealed interface ReadingValidation {
    data class Accepted(
        val candidate: Reading,
        val createdInstallation: MeterInstallation? = null,
    ) : ReadingValidation
    data class Rejected(val errors: ReadingErrors) : ReadingValidation {
        init {
            require(!errors.isEmpty) { "Rejected reading validation requires at least one error" }
        }
    }
}

/** Shared by live date selection and final save; an edit cannot conflict with itself. */
fun validateReadingDate(readings: List<Reading>, id: String?, date: LocalDate?, today: LocalDate): ReadingError? =
    validateReadingDate(TrackerData(readings), id, date, today)

/** Selects the installation that was current on the reading date. */
fun installationForDate(tracker: TrackerData, date: LocalDate): MeterInstallation? {
    return tracker.installations.filter { it.startDate == null || it.startDate <= date }
        .maxWithOrNull(compareBy<MeterInstallation> { it.startDate != null }.thenBy { it.startDate })
}

fun validateReadingDate(tracker: TrackerData, id: String?, date: LocalDate?, today: LocalDate): ReadingError? {
    val installationId = tracker.readings.firstOrNull { it.id == id }?.installationId
        ?: date?.let { installationForDate(tracker, it)?.id }
    return when {
        date == null -> ReadingError.DATE_REQUIRED
        date > today -> ReadingError.FUTURE_DATE
        installationId == null && (tracker.readings.isNotEmpty() || tracker.installations.isNotEmpty()) ->
            ReadingError.BEFORE_METER_START_DATE
        tracker.readings.any { it.id != id && it.installationId == installationId && it.date == date } -> ReadingError.DUPLICATE_DATE
        else -> null
    }
}

/** Pure Add policy; callers supply the latest transactional rows and a new identity. */
fun evaluateSave(
    readings: List<Reading>,
    intent: SaveReadingIntent.Add,
    addId: String,
): ReadingValidation = evaluateSave(TrackerData(readings), intent, addId, addId)

fun evaluateSave(
    tracker: TrackerData,
    intent: SaveReadingIntent.Add,
    addId: String,
    newInstallationId: String = addId,
): ReadingValidation {
    val createdInstallation = if (tracker.installations.isEmpty() && tracker.readings.isEmpty()) {
        intent.date?.let { MeterInstallation(newInstallationId, it) }
    } else {
        null
    }
    val installationId = createdInstallation?.id ?: intent.date?.let { installationForDate(tracker, it)?.id }
    return evaluateCandidate(
        tracker, addId, intent.date, intent.meter, intent.today, installationId,
        time = intent.time, zone = intent.zone, now = intent.now,
        newInstallation = createdInstallation,
    )
}

/** Pure Edit policy; the latest persisted target supplies its immutable identity and date. */
fun evaluateSave(readings: List<Reading>, intent: SaveReadingIntent.Edit): ReadingValidation {
    return evaluateSave(TrackerData(readings), intent)
}

fun evaluateSave(tracker: TrackerData, intent: SaveReadingIntent.Edit): ReadingValidation {
    val existing = tracker.readings.firstOrNull { it.id == intent.id }
        ?: return ReadingValidation.Rejected(ReadingErrors(value = ReadingError.MISSING_READING))
    return evaluateCandidate(
        tracker, existing.id, existing.date, intent.meter, intent.today, existing.installationId, intent.note,
        intent.time, existing.zone, intent.now,
        existing.observedAt.takeIf { intent.time == existing.localTime.toString() },
    )
}

internal fun evaluateCandidate(
    tracker: TrackerData,
    id: String,
    date: LocalDate?,
    meter: ParsedMeter,
    today: LocalDate,
    installationId: String?,
    note: String? = null,
    time: String,
    zone: ZoneId,
    now: Instant,
    unchangedInstant: Instant? = null,
    newInstallation: MeterInstallation? = null,
) : ReadingValidation {
    val others = tracker.readings.filter { it.id != id && it.installationId == installationId }
    val dateError = if (newInstallation == null) {
        validateReadingDate(tracker, id, date, today)
    } else {
        when {
            date == null -> ReadingError.DATE_REQUIRED
            date > today -> ReadingError.FUTURE_DATE
            else -> null
        }
    }
    val value = (meter as? ParsedMeter.Value)?.cubicMetres
    val valueError = when {
        meter == ParsedMeter.Required -> ReadingError.VALUE_REQUIRED
        value == null -> ReadingError.INVALID_NUMBER
        value.signum() < 0 -> ReadingError.NEGATIVE_VALUE
        date != null && others.filter { it.date < date }.maxByOrNull { it.date }?.let { value < it.cubicMetres } == true ->
            ReadingError.BELOW_PREVIOUS
        date != null && others.filter { it.date > date }.minByOrNull { it.date }?.let { value > it.cubicMetres } == true ->
            ReadingError.ABOVE_NEXT
        else -> null
    }
    val instant = unchangedInstant ?: date?.let { observationInstant(it, time, zone) }
    val timeError = when {
        time.isBlank() -> ReadingError.TIME_REQUIRED
        date == null -> null
        instant == null -> ReadingError.INVALID_TIME
        instant > now && dateError != ReadingError.FUTURE_DATE -> ReadingError.FUTURE_TIME
        others.any { (it.date < date && it.observedAt >= instant) || (it.date > date && it.observedAt <= instant) } ->
            ReadingError.TIME_ORDER
        else -> null
    }
    val errors = ReadingErrors(dateError, valueError, timeError)
    if (!errors.isEmpty) return ReadingValidation.Rejected(errors)
    return ReadingValidation.Accepted(
        Reading(id, checkNotNull(date), checkNotNull(value), checkNotNull(installationId), note?.takeUnless { it.isBlank() },
            checkNotNull(instant), zone),
        newInstallation,
    )
}
