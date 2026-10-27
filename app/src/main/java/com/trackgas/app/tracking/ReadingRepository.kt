package com.trackgas.app.tracking

import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow

class ReadingStorageException(cause: Throwable) : Exception("Reading storage failed", cause)

sealed interface SaveReadingResult {
    data class Saved(val readings: List<Reading>) : SaveReadingResult
    data class Invalid(val errors: ReadingErrors) : SaveReadingResult
}

sealed interface DeleteReadingResult {
    data class Deleted(val readings: List<Reading>) : DeleteReadingResult
    data object Missing : DeleteReadingResult
}

interface ReadingRepository {
    fun observeReadings(): Flow<List<Reading>>

    suspend fun save(
        id: String?,
        date: LocalDate?,
        input: String,
        today: LocalDate,
        locale: Locale,
    ): SaveReadingResult

    suspend fun delete(id: String): DeleteReadingResult
}
