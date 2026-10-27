package com.trackgas.app.data

import android.database.SQLException
import androidx.room.withTransaction
import com.trackgas.app.tracking.DeleteReadingResult
import com.trackgas.app.tracking.Reading
import com.trackgas.app.tracking.ReadingError
import com.trackgas.app.tracking.ReadingErrors
import com.trackgas.app.tracking.ReadingRepository
import com.trackgas.app.tracking.ReadingStorageException
import com.trackgas.app.tracking.SaveReadingResult
import com.trackgas.app.tracking.validateReading
import java.io.IOException
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class RoomReadingRepository(private val database: ReadingDatabase) : ReadingRepository {
    private val dao = database.readings()

    override fun observeReadings(): Flow<List<Reading>> =
        dao.observeAll().map { rows -> rows.map { it.toReading() } }
            .catch { failure -> throw storageFailure(failure) }

    override suspend fun save(
        id: String?,
        date: LocalDate?,
        input: String,
        today: LocalDate,
        locale: Locale,
    ): SaveReadingResult = storageOperation {
        database.withTransaction {
            val readings = dao.getAll().map { it.toReading() }
            val existing = id?.let { target -> readings.firstOrNull { it.id == target } }
            if (id != null && existing == null) {
                return@withTransaction SaveReadingResult.Invalid(ReadingErrors(value = ReadingError.MISSING_READING))
            }
            val validation = validateReading(
                readings, id ?: UUID.randomUUID().toString(), existing?.date ?: date, input, today, locale,
            )
            val candidate = validation.candidate
                ?: return@withTransaction SaveReadingResult.Invalid(validation.errors)
            if (existing == null) {
                dao.insert(candidate.toEntity())
            } else {
                check(dao.update(candidate.toEntity()) == 1) { "Reading disappeared during update" }
            }
            SaveReadingResult.Saved(dao.getAll().map { it.toReading() })
        }
    }

    override suspend fun delete(id: String): DeleteReadingResult = storageOperation {
        database.withTransaction {
            if (dao.delete(id) == 0) {
                DeleteReadingResult.Missing
            } else {
                DeleteReadingResult.Deleted(dao.getAll().map { it.toReading() })
            }
        }
    }

    private suspend fun <T> storageOperation(block: suspend () -> T): T = try {
        block()
    } catch (error: SQLException) {
        throw ReadingStorageException(error)
    } catch (error: IOException) {
        throw ReadingStorageException(error)
    } catch (error: DateTimeException) {
        throw ReadingStorageException(error)
    } catch (error: IllegalArgumentException) {
        throw ReadingStorageException(error)
    } catch (error: CancellationException) {
        throw error
    } catch (error: IllegalStateException) {
        throw ReadingStorageException(error)
    }

    private fun storageFailure(error: Throwable): Throwable = when (error) {
        is CancellationException -> error
        is SQLException, is IOException, is DateTimeException,
        is IllegalArgumentException, is IllegalStateException -> ReadingStorageException(error)
        else -> error
    }
}
