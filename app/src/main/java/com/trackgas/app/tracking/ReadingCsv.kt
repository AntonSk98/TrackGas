package com.trackgas.app.tracking

fun readingCsv(tracker: TrackerData): ByteArray {
    val installationOrder = tracker.installations
        .sortedWith(compareBy<MeterInstallation> { it.startDate != null }.thenBy { it.startDate })
        .mapIndexed { index, installation -> installation.id to index }
        .toMap()
    val rows = tracker.readings.sortedWith(
        compareBy<Reading> { it.date }.thenBy { reading ->
            requireNotNull(installationOrder[reading.installationId]) { "Unknown installation" }
        },
    )
    return buildString {
        append("meter_id;date;meter_value\n")
        rows.forEach { reading ->
            append(csvField(reading.installationId))
            append(';')
            append(csvField(reading.date.toString()))
            append(';')
            append(csvField(reading.cubicMetres.toPlainString()))
            append('\n')
        }
    }.toByteArray(Charsets.UTF_8)
}

private fun csvField(value: String): String =
    if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
