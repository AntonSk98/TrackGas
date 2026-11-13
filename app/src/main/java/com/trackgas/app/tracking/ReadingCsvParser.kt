package com.trackgas.app.tracking

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/** One parsed CSV record. [physicalRow] is the one-based line where the record starts. */
data class ReadingCsvRecord(val physicalRow: Int, val fields: List<String>)

sealed interface ReadingCsvParseResult {
    data class Parsed(val records: List<ReadingCsvRecord>) : ReadingCsvParseResult
    data class Malformed(val physicalRow: Int) : ReadingCsvParseResult
}

enum class CsvImportErrorCode {
    CSV_HEADER_INVALID,
    CSV_ROW_INVALID,
    CSV_METER_ID_INVALID,
    CSV_TIMESTAMP_INVALID,
    CSV_TIMEZONE_INVALID,
    CSV_VALUE_INVALID,
    CSV_VALUE_NEGATIVE,
    CSV_METER_HISTORY_INVALID,
    CSV_FILE_READ_FAILED,
    CSV_IMPORT_FAILED,
}

enum class CsvImportField(val csvName: String) {
    METER_ID("meter_id"), TIMESTAMP("timestamp"), TIMEZONE("timezone"), METER_VALUE("meter_value"),
}

data class CsvImportError(
    val code: CsvImportErrorCode,
    val row: Int? = null,
    val field: CsvImportField? = null,
)

data class CsvReadingRow(
    val installationId: String,
    val date: LocalDate,
    val cubicMetres: BigDecimal,
    val physicalRow: Int,
    val note: String? = null,
    val observedAt: java.time.Instant,
    val zone: java.time.ZoneId,
)

sealed interface CsvReadingValidation {
    data class Valid(val rows: List<CsvReadingRow>) : CsvReadingValidation
    data class Invalid(val error: CsvImportError) : CsvReadingValidation
}

/** Validates the exact export header and field formats without consulting storage. */
fun validateReadingCsv(
    text: String, knownInstallationIds: Set<String>, now: java.time.Instant = java.time.Instant.now(),
): CsvReadingValidation {
    val records = when (val parsed = parseReadingCsv(text)) {
        is ReadingCsvParseResult.Malformed -> return CsvReadingValidation.Invalid(
            CsvImportError(
                if (parsed.physicalRow == 1) CsvImportErrorCode.CSV_HEADER_INVALID else CsvImportErrorCode.CSV_ROW_INVALID,
                parsed.physicalRow,
            ),
        )
        is ReadingCsvParseResult.Parsed -> parsed.records
    }
    val header = records.firstOrNull()?.fields
    val expectedHeader = listOf("meter_id", "timestamp", "timezone", "meter_value", "note")
    if (header != expectedHeader) {
        return CsvReadingValidation.Invalid(CsvImportError(CsvImportErrorCode.CSV_HEADER_INVALID, 1))
    }
    val expectedFieldCount = expectedHeader.size

    val knownByUuid = knownInstallationIds.mapNotNull { id ->
        parseCanonicalUuid(id)?.let { it to id }
    }.toMap()
    val rows = mutableListOf<CsvReadingRow>()
    for (record in records.drop(1)) {
        if (record.fields.size != expectedFieldCount) return CsvReadingValidation.Invalid(
            CsvImportError(CsvImportErrorCode.CSV_ROW_INVALID, record.physicalRow),
        )
        val meterUuid = parseCanonicalUuid(record.fields[0]) ?: return CsvReadingValidation.Invalid(
            CsvImportError(CsvImportErrorCode.CSV_METER_ID_INVALID, record.physicalRow, CsvImportField.METER_ID),
        )
        val installationId = knownByUuid[meterUuid] ?: meterUuid
        val zone = try { java.time.ZoneId.of(record.fields[2]) } catch (_: java.time.DateTimeException) {
            null
        } ?: return CsvReadingValidation.Invalid(
            CsvImportError(CsvImportErrorCode.CSV_TIMEZONE_INVALID, record.physicalRow, CsvImportField.TIMEZONE),
        )
        val timestamp = try {
            record.fields[1].takeIf {
                Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(?:Z|[+-][0-9]{2}:[0-9]{2})").matches(it)
            }?.let(java.time.OffsetDateTime::parse)?.takeIf {
                it.offset in zone.rules.getValidOffsets(it.toLocalDateTime()) && it.toInstant() <= now
            }
        } catch (_: java.time.DateTimeException) { null } ?: return CsvReadingValidation.Invalid(
            CsvImportError(CsvImportErrorCode.CSV_TIMESTAMP_INVALID, record.physicalRow, CsvImportField.TIMESTAMP),
        )
        val date = timestamp.toLocalDate()
        val valueText = record.fields[3]
        if (!METER_VALUE_PATTERN.matches(valueText)) return CsvReadingValidation.Invalid(
            CsvImportError(CsvImportErrorCode.CSV_VALUE_INVALID, record.physicalRow, CsvImportField.METER_VALUE),
        )
        val value = try { BigDecimal(valueText) } catch (_: NumberFormatException) {
            return CsvReadingValidation.Invalid(
                CsvImportError(CsvImportErrorCode.CSV_VALUE_INVALID, record.physicalRow, CsvImportField.METER_VALUE),
            )
        }
        if (value.signum() < 0) return CsvReadingValidation.Invalid(
            CsvImportError(CsvImportErrorCode.CSV_VALUE_NEGATIVE, record.physicalRow, CsvImportField.METER_VALUE),
        )
        rows += CsvReadingRow(
            installationId,
            date,
            value,
            record.physicalRow,
            record.fields[4].takeUnless { it.isBlank() },
            timestamp.toInstant(), zone,
        )
    }

    // Updating a LinkedHashMap entry keeps output deterministic while retaining the last value.
    val deduplicated = linkedMapOf<Pair<String, LocalDate>, CsvReadingRow>()
    rows.forEach { deduplicated[it.installationId to it.date] = it }
    return CsvReadingValidation.Valid(deduplicated.values.toList())
}

private fun parseCanonicalUuid(value: String): String? {
    if (!UUID_PATTERN.matches(value)) return null
    return try {
        UUID.fromString(value).toString()
    } catch (_: IllegalArgumentException) {
        null
    }
}

private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
private val METER_VALUE_PATTERN = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")

/** Builds a complete candidate without mutating [base]; imported meters become independent histories. */
internal fun csvImportCandidate(
    base: TrackerData,
    rows: List<CsvReadingRow>,
    newReadingId: () -> String,
): TrackerData {
    val affected = rows.map { it.installationId }.toSet()
    val retained = base.readings.filterNot { it.installationId in affected }
    val installationIds = base.installations.mapTo(mutableSetOf()) { it.id }
    val newInstallations = affected.filter { installationIds.add(it) }.map { MeterInstallation(it) }
    val identities = retained.mapTo(mutableSetOf()) { it.id }
    val imported = rows.map { row ->
        var id: String
        do { id = newReadingId() } while (id.isBlank() || !identities.add(id))
        Reading(id, row.date, row.cubicMetres, row.installationId, row.note, row.observedAt, row.zone)
    }
    return base.copy(readings = retained + imported, installations = base.installations + newInstallations)
}

internal sealed interface CsvImportCandidateValidation {
    data class Valid(val tracker: TrackerData) : CsvImportCandidateValidation
    data class Invalid(val error: CsvImportError) : CsvImportCandidateValidation
}

internal fun validateCsvImportCandidate(
    base: TrackerData,
    rows: List<CsvReadingRow>,
    newReadingId: () -> String,
): CsvImportCandidateValidation {
    val candidate = csvImportCandidate(base, rows, newReadingId)
    val violation = readingCollectionViolation(candidate)
        ?: return CsvImportCandidateValidation.Valid(candidate)
    return CsvImportCandidateValidation.Invalid(csvImportHistoryError(candidate, rows, violation))
}

private fun csvImportHistoryError(
    candidate: TrackerData,
    rows: List<CsvReadingRow>,
    violation: ReadingCollectionViolation,
): CsvImportError {
    val source = when (violation) {
        ReadingCollectionViolation.DECREASING_VALUE, ReadingCollectionViolation.TIMESTAMP_ORDER -> candidate.readings.groupBy { it.installationId }
            .values.asSequence()
            .flatMap { meterReadings -> meterReadings.sortedBy { it.date }.zipWithNext().asSequence() }
            .firstOrNull { (earlier, later) -> earlier.cubicMetres > later.cubicMetres || earlier.observedAt >= later.observedAt }
            ?.second
            ?.let { offending -> rows.firstOrNull { it.installationId == offending.installationId && it.date == offending.date } }
        ReadingCollectionViolation.OVERLAPPING_COVERAGE -> overlappingReadingCoverage(candidate.readings)
            ?.let { (first, second) ->
                listOf(first.later, second.later).firstNotNullOfOrNull { reading ->
                    rows.firstOrNull { it.installationId == reading.installationId && it.date == reading.date }
                }
            }
        else -> null
    }
    val field = when (violation) {
        ReadingCollectionViolation.DECREASING_VALUE -> CsvImportField.METER_VALUE
        ReadingCollectionViolation.OVERLAPPING_COVERAGE, ReadingCollectionViolation.TIMESTAMP_ORDER -> CsvImportField.TIMESTAMP
        else -> null
    }
    return CsvImportError(CsvImportErrorCode.CSV_METER_HISTORY_INVALID, source?.physicalRow, field)
}

/** Pure parser for the semicolon-delimited dialect emitted by [readingCsv]. */
fun parseReadingCsv(text: String): ReadingCsvParseResult {
    if (text.isEmpty()) return ReadingCsvParseResult.Parsed(emptyList())

    val records = mutableListOf<ReadingCsvRecord>()
    var fields = mutableListOf<String>()
    val field = StringBuilder()
    var rowStart = 1
    var physicalRow = 1
    var index = 0
    var quoted = false
    var closedQuote = false
    var atFieldStart = true
    var recordStarted = false

    fun finishField() {
        fields += field.toString()
        field.setLength(0)
        atFieldStart = true
        closedQuote = false
    }

    fun finishRecord() {
        finishField()
        records += ReadingCsvRecord(rowStart, fields.toList())
        fields = mutableListOf()
        recordStarted = false
        rowStart = physicalRow + 1
    }

    while (index < text.length) {
        val char = text[index]
        if (quoted) {
            when (char) {
                '"' -> {
                    if (text.getOrNull(index + 1) == '"') {
                        field.append('"')
                        index++
                    } else {
                        quoted = false
                        closedQuote = true
                    }
                }
                '\r' -> {
                    if (text.getOrNull(index + 1) == '\n') {
                        field.append("\r\n")
                        index++
                    } else field.append(char)
                    physicalRow++
                }
                '\n' -> {
                    field.append(char)
                    physicalRow++
                }
                else -> field.append(char)
            }
            recordStarted = true
            index++
            continue
        }

        when {
            closedQuote && char != ';' && char != '\r' && char != '\n' ->
                return ReadingCsvParseResult.Malformed(rowStart)
            char == ';' -> {
                finishField()
                recordStarted = true
            }
            char == '\r' || char == '\n' -> {
                finishRecord()
                if (char == '\r' && text.getOrNull(index + 1) == '\n') index++
                physicalRow++
                rowStart = physicalRow
            }
            atFieldStart && char == '"' -> {
                quoted = true
                atFieldStart = false
                recordStarted = true
            }
            else -> {
                if (!atFieldStart && char == '"') return ReadingCsvParseResult.Malformed(rowStart)
                field.append(char)
                atFieldStart = false
                recordStarted = true
            }
        }
        index++
    }

    if (quoted) return ReadingCsvParseResult.Malformed(rowStart)
    if (recordStarted || fields.isNotEmpty() || field.isNotEmpty() || closedQuote || !atFieldStart) {
        finishRecord()
    }
    return ReadingCsvParseResult.Parsed(records)
}
