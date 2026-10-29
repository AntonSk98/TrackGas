package com.trackgas.app.tracking

import java.io.IOException
import java.io.OutputStream

fun writeReadingCsv(tracker: TrackerData, open: () -> OutputStream?): Boolean {
    val bytes = readingCsv(tracker)
    val stream = try {
        open() ?: return false
    } catch (_: IOException) {
        return false
    } catch (_: SecurityException) {
        return false
    } catch (_: IllegalArgumentException) {
        return false
    }
    return try {
        stream.use { it.write(bytes) }
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
